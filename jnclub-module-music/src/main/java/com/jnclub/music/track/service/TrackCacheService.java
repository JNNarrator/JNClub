package com.jnclub.music.track.service;

import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.RedisLock;
import com.jnclub.music.track.domain.Track;
import com.jnclub.music.track.dto.MediaUrlDTO;
import com.jnclub.music.track.mapper.TrackMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class TrackCacheService {

    private static final Logger log = LoggerFactory.getLogger(TrackCacheService.class);
    /**
     * L1 缓存名，必须与 {@code TrackServiceImpl.getMediaUrl} 读取的缓存名一致，
     * 且存放 {@link MediaUrlDTO}（读取侧按该类型强转）。
     * <p>此前此处误用 "cachedMediaUrl" 并存放 String，导致定时刷新的结果写进了一个
     * 无人读取的缓存，对播放路径完全无效。</p>
     */
    private static final String CACHE_NAME = "mediaUrls";
    private static final Duration SCHEDULED_LOCK_TTL = Duration.ofMinutes(10);
    /**
     * 续期提前量：直链剩余有效期低于该值即提前换新。
     * <p>蓝奏云直链仅约 45 分钟有效，必须在失效前完成续期，才能保证用户随时点播都有可用链接。</p>
     */
    private static final Duration REFRESH_AHEAD = Duration.ofMinutes(20);
    /** 单轮续期的总时间上限：超过即收尾，剩余曲目交由下一轮巡检补齐。 */
    private static final long MAX_ROUND_TIMEOUT_MS = Duration.ofMinutes(20).toMillis();
    /** 续期并发度：取链是 3~5 次蓝奏云 HTTP 的串行过程，串行续期会让一轮巡检耗时极长；并发过高易触发反爬。 */
    private static final int REFRESH_CONCURRENCY = 4;

    private final TrackMapper trackMapper;
    private final TrackService trackService;
    private final CacheManager cacheManager;
    private final RedisLock redisLock;

    /** 续期专用线程池（守护线程），避免定时任务串行取链导致续期窗口被拖长。 */
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(
            REFRESH_CONCURRENCY, daemonThreadFactory("music-url-refresh-"));

    /**
     * 轮次调度线程池（单线程）。
     * <p>必须与 {@link #refreshExecutor} 分开：轮次任务内部会向 refreshExecutor 提交取链任务并等待结果，
     * 若两者共用同一个池，「调度任务占住线程 → 等待同池任务」在并发度不足时会互相饿死。
     * 单线程也保证了同一时刻只有一个轮次在排队/执行。</p>
     */
    private final ExecutorService roundExecutor = Executors.newSingleThreadExecutor(
            daemonThreadFactory("music-url-round-"));

    /** 守护线程工厂：命名 + daemon，避免线程池阻止 JVM 退出。 */
    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    @jakarta.annotation.PreDestroy
    void shutdownExecutors() {
        roundExecutor.shutdownNow();
        refreshExecutor.shutdownNow();
    }

    private final AtomicInteger refreshTotal = new AtomicInteger(0);
    private final AtomicInteger refreshCompleted = new AtomicInteger(0);
    /** 真正刷新成功的数量：与 refreshCompleted（已处理数）区分，避免全部失败时日志仍显示 "完成 N/N"。 */
    private final AtomicInteger refreshSucceeded = new AtomicInteger(0);
    /** 本轮中「源文件已永久不可用」的数量：属正常情况，不应触发失败告警。 */
    private final AtomicInteger refreshSourceGone = new AtomicInteger(0);
    /**
     * 本轮是否已被抢占（排队或执行中），供 {@code /tracks/cache/status} 对外展示。
     * <p>语义是「已抢占名额」而非「正在执行」：这样调用方触发刷新后立即轮询就能看到 inProgress=true，
     * 不会因为后台线程还没开始跑而被误判为「刷新已完成」。</p>
     */
    private volatile boolean refreshing = false;
    private volatile boolean initialized = false;

    /**
     * 抢占本轮刷新名额。成功返回 true，表示调用方负责在本轮结束时调用 {@link #endRound()}。
     * <p>必须是同步的：{@code manualRefresh} 原先依赖 {@code @Async} 在后台置位，
     * Controller 返回时 {@code refreshing} 可能仍为 false，前端随即轮询就会误判为「已完成」。</p>
     */
    private synchronized boolean beginRound() {
        if (refreshing) return false;
        refreshing = true;
        return true;
    }

    /** 释放本轮名额。 */
    private synchronized void endRound() {
        refreshing = false;
    }

    /** 提交一个轮次任务到调度线程池；任务体内部必须自行 endRound。 */
    private void submitRound(Runnable task) {
        try {
            roundExecutor.execute(() -> {
                try {
                    task.run();
                } finally {
                    endRound();
                }
            });
        } catch (RuntimeException e) {
            // 线程池已关闭（如应用停机中）：立即释放名额，避免 refreshing 永久停留在 true
            endRound();
            throw e;
        }
    }

    public TrackCacheService(TrackMapper trackMapper, TrackService trackService, CacheManager cacheManager, RedisLock redisLock) {
        this.trackMapper = trackMapper;
        this.trackService = trackService;
        this.cacheManager = cacheManager;
        this.redisLock = redisLock;
    }

    @PostConstruct
    void onStartup() {
        // 非阻塞：交给调度线程池（守护线程），不拖慢 Spring Boot 的 Tomcat 启动。
        // 走 submitRound 抢占名额，预热+首轮续期期间定时巡检不会重复触发。
        if (!beginRound()) return;
        submitRound(() -> {
            try {
                Thread.sleep(1000);
                initCache();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("TrackCacheService 后台预热异常", e);
            }
        });
    }

    /**
     * 预热 L1 缓存并按需补一轮续期。
     * <p>注意：不加 {@code @Async}。此前标注过但它是本类内部调用（self-invocation），
     * 不经过 Spring 代理，注解从未生效，容易误导后续维护者。</p>
     */
    public void initCache() {
        log.info("TrackCacheService: 从 MySQL 预热 L1 缓存...");
        List<Track> all = trackMapper.selectList(null);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            for (Track t : all) {
                // 只预热「有效期内且上次预检可播」的直链。
                // 此前不校验 playable，会把已判定不可播的曲目也预热成 playable=true，
                // 让 getMediaUrl 的 L1 判定直接命中，等于绕过了健康预检与回源刷新。
                if (t.getMediaUrl() != null && !t.getMediaUrl().isBlank()
                        && t.getUrlExpiresAt() != null && t.getUrlExpiresAt().isAfter(OffsetDateTime.now())
                        && (t.getPlayable() == null || t.getPlayable() == 1)) {
                    cache.put(t.getTrackId(), MediaUrlDTO.builder()
                            .trackId(t.getTrackId())
                            .mediaUrl(t.getMediaUrl())
                            .format(t.getFormat() != null ? t.getFormat() : "")
                            .expiresAt(t.getUrlExpiresAt())
                            .playable(true)
                            .build());
                }
            }
        }
        initialized = true;
        log.info("TrackCacheService: L1 预热完成 {} 首", all.size());
        // 逐首刷新「缺失或余量不足」的 URL，失败时保留旧值，避免蓝奏云失效时清空所有链接
        List<String> expired = new ArrayList<>();
        OffsetDateTime startupThreshold = OffsetDateTime.now().plus(REFRESH_AHEAD);
        for (Track t : all) {
            if (t.getMediaUrl() == null || t.getUrlExpiresAt() == null
                    || !t.getUrlExpiresAt().isAfter(startupThreshold)) {
                expired.add(t.getTrackId());
            }
        }
        if (!expired.isEmpty()) {
            refreshAll(expired);
        }
    }

    /**
     * 直链续期巡检。
     * <p>蓝奏云直链仅约 45 分钟有效，必须在失效前提前换新。此前为「每 2 小时全量刷新」，
     * 间隔远大于直链有效期，导致每轮刷新前都有近一小时全站歌曲取不到可用链接（空窗）。
     * 现改为每 5 分钟巡检一次，只对剩余有效期不足 {@link #REFRESH_AHEAD} 的曲目续期，
     * 使链接在被使用前始终保有充足余量，同时避免无谓的回源请求。</p>
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scheduledRefresh() {
        // 先同步抢占本轮名额，再交给调度线程池执行：
        // 不再占用 @Scheduled 的调度线程做长达数分钟的取链，也避免与手动刷新互相踩踏。
        if (!beginRound()) {
            log.debug("TrackCacheService: 已有刷新轮次在执行，本轮巡检跳过");
            return;
        }
        submitRound(this::runScheduledRound);
    }

    /** 一轮定时巡检的实际执行体（已在 roundExecutor 线程上，名额由 submitRound 负责释放）。 */
    private void runScheduledRound() {
        String lockKey = CacheKey.lock("scheduled", "track-refresh");
        String token = redisLock.tryLock(lockKey, SCHEDULED_LOCK_TTL);
        if (token == null) {
            log.warn("TrackCacheService: 未取得续期锁（上一轮可能仍在执行，或 Redis 异常），本轮跳过");
            return;
        }
        try {
            List<String> ids = trackService.getAllTrackIds();
            if (ids.isEmpty()) {
                // 曲目列表依赖蓝奏云目录接口：为空说明目录/会话异常，此时续期被静默跳过，
                // 会导致所有直链在 45 分钟后集体失效且无人知晓，必须显式告警。
                log.error("TrackCacheService: 未获取到任何曲目（蓝奏云目录/会话可能异常），本次直链续期被跳过！");
                return;
            }
            List<String> due = selectDueForRefresh(ids);
            if (due.isEmpty()) {
                log.debug("TrackCacheService: 全部直链余量充足，本轮无需续期");
                return;
            }
            log.info("TrackCacheService: {} 首直链余量不足 {} 分钟，开始续期", due.size(), REFRESH_AHEAD.toMinutes());
            refreshAll(due);
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    /**
     * 挑出需要续期的曲目：没有直链，或剩余有效期不足 {@link #REFRESH_AHEAD}。
     * <p>以蓝奏云目录返回的 id 为准（避免已删除曲目残留），过期时间取自 MySQL。</p>
     */
    private List<String> selectDueForRefresh(List<String> ids) {
        OffsetDateTime threshold = OffsetDateTime.now().plus(REFRESH_AHEAD);
        Map<String, Track> cached = new java.util.HashMap<>();
        for (Track t : trackMapper.selectList(null)) {
            cached.put(t.getTrackId(), t);
        }
        List<String> due = new ArrayList<>();
        for (String id : ids) {
            Track t = cached.get(id);
            // 源文件已永久不可用（分享被取消/文件不存在）→ 跳过：重试无意义，
            // 否则每轮巡检都会为这些曲目白白回源并刷失败日志。
            if (t != null && TrackService.SOURCE_GONE.equals(t.getLastError())) {
                continue;
            }
            if (t == null || t.getMediaUrl() == null || t.getMediaUrl().isBlank()
                    || t.getUrlExpiresAt() == null || !t.getUrlExpiresAt().isAfter(threshold)) {
                due.add(id);
            }
        }
        return due;
    }

    /**
     * 批量续期：从 lanzou 获取指定 trackId 列表的直链 → 写入/更新 MySQL + L1，失败时保留旧值。
     * <p>两项优化：
     * <ul>
     *   <li>并发取链（{@link #REFRESH_CONCURRENCY}）。取链本身是 3~5 次串行 HTTP，串行续期会让
     *       一轮巡检耗时随曲目数线性增长，极端情况下「上一轮还没跑完下一轮就该开始了」。</li>
     *   <li>不再每首重复读库。{@code refreshMediaUrl} 已负责落库（定向 UPDATE），这里只用一次
     *       批量查询判断哪些 trackId 库里还没有，仅为这些补一条基础记录。</li>
     * </ul></p>
     */
    private void refreshAll(List<String> ids) {
        // 互斥已由上层 beginRound/endRound 承担，这里不再自行抢占名额。
        try {
            // 注意：不再先清空所有 URL，防止蓝奏云失效时所有歌曲失去播放链接
            refreshTotal.set(ids.size());
            refreshCompleted.set(0);
            refreshSucceeded.set(0);
            refreshSourceGone.set(0);
            Cache cache = cacheManager.getCache(CACHE_NAME);

            // 一次批量查询确认库中已存在的曲目，替代原先「每首一次 selectById」
            Set<String> existingIds;
            try {
                existingIds = trackMapper.selectBatchIds(ids).stream()
                        .map(Track::getTrackId)
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("TrackCacheService: 批量查询曲目失败，本轮跳过补录逻辑: {}", e.getMessage());
                existingIds = Set.copyOf(ids);
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>(ids.size());
            for (String id : ids) {
                boolean exists = existingIds.contains(id);
                futures.add(CompletableFuture.runAsync(
                        () -> refreshOne(id, exists, cache), refreshExecutor));
            }
            awaitAll(futures, ids.size());

            int ok = refreshSucceeded.get(), total = ids.size(), gone = refreshSourceGone.get();
            if (total > 0 && ok == 0 && gone < total) {
                // 全军覆没通常意味着蓝奏云改版/会话异常，必须显式告警而非静默"完成"
                log.error("TrackCacheService: 直链刷新全部失败 0/{}，播放链接将无法续期，请检查蓝奏云取链逻辑！", total);
            } else if (total > 0 && ok == 0) {
                log.warn("TrackCacheService: 本轮 {} 首均为源文件已不可用（分享取消等），已标记跳过", total);
            } else {
                log.info("TrackCacheService: 刷新完成 成功 {}/{}（已处理 {}）", ok, total, refreshCompleted.get());
            }
        } catch (Exception e) {
            // 单首失败已在 refreshOne 内部处理；这里只兜底记录，避免异常冒泡打断轮次收尾
            log.warn("TrackCacheService: 本轮续期异常结束: {}", e.getMessage());
        }
    }

    /** 续期单首：强制回源取新直链（由 TrackService 落库），成功则刷新 L1；失败保留旧值。 */
    private void refreshOne(String id, boolean existsInDb, Cache cache) {
        try {
            // 使用强制刷新：绕过 @Cacheable 和有效期检查，直接调蓝奏云拉取新直链
            var dto = trackService.refreshMediaUrl(id);
            if (dto == null || dto.getMediaUrl() == null || dto.getMediaUrl().isBlank()
                    || !Boolean.TRUE.equals(dto.getPlayable())) {
                log.warn("TrackCacheService: {} 刷新失败，保留旧值", id);
                return;
            }
            if (!existsInDb) {
                // 首次出现的曲目：补一条基础记录，后续直链维护走定向 UPDATE
                Track t = new Track();
                t.setTrackId(id);
                t.setName(id);
                t.setArtist("");
                t.setDuration(0);
                t.setFormat(dto.getFormat() == null ? "" : dto.getFormat());
                t.setHasLyric(false);
                t.setFileSize(0L);
                t.setMediaUrl(dto.getMediaUrl());
                t.setUrlExpiresAt(dto.getExpiresAt());
                t.setPlayable(1);
                t.setLastError(null);
                try {
                    trackMapper.insert(t);
                } catch (Exception e) {
                    log.warn("TrackCacheService: {} 补录曲目失败: {}", id, e.getMessage());
                }
            }
            if (cache != null) cache.put(id, dto);
            refreshSucceeded.incrementAndGet();
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (msg.contains("cancelled") || msg.contains("not exist")
                    || msg.contains("取消") || msg.contains("不存在")) {
                // 源文件本身没了（分享被取消等），属正常情况，已由 TrackService 打标跳过
                log.warn("TrackCacheService: {} 源文件已不可用，已标记跳过: {}", id, e.getMessage());
                refreshSourceGone.incrementAndGet();
            } else {
                log.warn("TrackCacheService: {} 刷新失败: {}，保留旧值", id, e.getMessage());
            }
        } finally {
            refreshCompleted.incrementAndGet();
        }
    }

    public void refreshTrackUrl(String trackId) {
        try {
            var dto = trackService.getMediaUrl(trackId);
            var t = trackMapper.selectById(trackId);
            if (t != null) {
                t.setMediaUrl(dto.getMediaUrl());
                t.setUrlExpiresAt(dto.getExpiresAt());
                trackMapper.updateById(t);
            }
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) cache.put(trackId, dto);
        } catch (Exception e) {
            log.warn("TrackCacheService: 单首刷新失败 {}", trackId);
        }
    }

    /**
     * 手动触发全量续期（管理端「刷新缓存」）。
     * <p>不再使用 {@code @Async}：改为同步抢占名额后再提交，这样调用方一返回
     * {@code /tracks/cache/status} 就能看到 {@code inProgress=true}，
     * 前端不会因为后台线程尚未启动而误判为「刷新已完成」。</p>
     *
     * @return true=已触发；false=已有刷新轮次在执行（排队或运行中）
     */
    public boolean manualRefresh() {
        if (!beginRound()) return false;
        submitRound(() -> {
            List<String> ids = trackService.getAllTrackIds();
            if (ids.isEmpty()) {
                log.error("TrackCacheService: 手动刷新未获取到任何曲目（蓝奏云目录/会话可能异常），本次跳过！");
                return;
            }
            refreshAll(ids);
        });
        return true;
    }

    /**
     * 等待本轮所有取链任务结束，带总超时。
     * <p>原先用无超时的 {@code join()}：单个任务卡住（网络挂起/反爬重试 sleep）会让调度线程无限等待，
     * 名额一直不释放，后续所有轮次都被跳过。</p>
     * <p>超时只记录告警并正常收尾：已完成的直链都已落库；未完成的曲目在下一轮
     * {@code selectDueForRefresh} 中仍会被挑出（判定依据是「余量不足」而非「本轮跑过」），因此可自然恢复。</p>
     * <p>注意（权衡）：超时收尾时 refreshExecutor 中未完成的取链任务仍在后台继续执行，
     * 因此下一轮可能与这些残余任务短暂重叠。两者共用同一个 4 线程池，实际并发上限不变，
     * 最坏后果只是少数曲目被重复取链一次（结果都会落库，后写覆盖先写，均为有效直链）。
     * 相比原先「无超时导致名额永久不释放、后续轮次全部被跳过」，这是更可接受的取舍。</p>
     */
    private void awaitAll(List<CompletableFuture<Void>> futures, int taskCount) {
        if (futures.isEmpty()) return;
        long perTaskMs = 60_000L;
        long budgetMs = Math.max(60_000L, (long) Math.ceil((double) taskCount / REFRESH_CONCURRENCY) * perTaskMs);
        budgetMs = Math.min(budgetMs, MAX_ROUND_TIMEOUT_MS);
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("TrackCacheService: 本轮续期超过 {}s 预算仍未跑完，先收尾（已完成 {} / 共 {}），剩余交由下一轮补齐",
                    budgetMs / 1000, refreshCompleted.get(), taskCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("TrackCacheService: 本轮续期被中断，先收尾");
        } catch (ExecutionException e) {
            // 任务体内部已自行吞掉异常，这里仅防御性兜底
            log.warn("TrackCacheService: 本轮续期任务异常: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }

    public boolean isInitialized() { return initialized; }
    public boolean isRefreshing() { return refreshing; }
    public Map<String, Object> getStatus() {
        return Map.of("total", refreshTotal.get(), "completed", refreshCompleted.get(), "inProgress", refreshing, "initialized", initialized);
    }
}
