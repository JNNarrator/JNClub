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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

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

    private final TrackMapper trackMapper;
    private final TrackService trackService;
    private final CacheManager cacheManager;
    private final RedisLock redisLock;

    private final AtomicInteger refreshTotal = new AtomicInteger(0);
    private final AtomicInteger refreshCompleted = new AtomicInteger(0);
    /** 真正刷新成功的数量：与 refreshCompleted（已处理数）区分，避免全部失败时日志仍显示 "完成 N/N"。 */
    private final AtomicInteger refreshSucceeded = new AtomicInteger(0);
    /** 本轮中「源文件已永久不可用」的数量：属正常情况，不应触发失败告警。 */
    private final AtomicInteger refreshSourceGone = new AtomicInteger(0);
    private volatile boolean refreshing = false;
    private volatile boolean initialized = false;

    public TrackCacheService(TrackMapper trackMapper, TrackService trackService, CacheManager cacheManager, RedisLock redisLock) {
        this.trackMapper = trackMapper;
        this.trackService = trackService;
        this.cacheManager = cacheManager;
        this.redisLock = redisLock;
    }

    @PostConstruct
    void onStartup() {
        // 非阻塞：在后台线程预热，不拖慢 Spring Boot 的 Tomcat 启动
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                initCache();
            } catch (Exception e) {
                log.warn("TrackCacheService 后台预热异常", e);
            }
        }, "cache-init").start();
    }

    @Async
    public void initCache() {
        log.info("TrackCacheService: 从 MySQL 预热 L1 缓存...");
        List<Track> all = trackMapper.selectList(null);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            for (Track t : all) {
                if (t.getMediaUrl() != null && !t.getMediaUrl().isBlank()
                        && t.getUrlExpiresAt() != null && t.getUrlExpiresAt().isAfter(OffsetDateTime.now())) {
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

    /** 全量刷新：从 lanzou 获取指定 trackId 列表的直链 → 写入/更新 MySQL + L1，失败时保留旧值 */
    private void refreshAll(List<String> ids) {
        if (refreshing) return;
        synchronized (this) { if (refreshing) return; refreshing = true; }
        try {
            // 注意：不再先清空所有 URL，防止蓝奏云失效时所有歌曲失去播放链接
            refreshTotal.set(ids.size());
            refreshCompleted.set(0);
            refreshSucceeded.set(0);
            refreshSourceGone.set(0);
            Cache cache = cacheManager.getCache(CACHE_NAME);

            for (String id : ids) {
                try {
                    // 使用强制刷新：绕过 @Cacheable 和有效期检查，直接调蓝奏云拉取新直链
                    var dto = trackService.refreshMediaUrl(id);
                    if (dto == null || dto.getMediaUrl() == null || dto.getMediaUrl().isBlank()
                            || !Boolean.TRUE.equals(dto.getPlayable())) {
                        log.warn("TrackCacheService: {} 刷新失败，保留旧值", id);
                        continue;
                    }
                    // upsert: 新增或更新 MySQL
                    var t = trackMapper.selectById(id);
                    if (t == null) {
                        t = new Track();
                        t.setTrackId(id);
                        t.setName(id);
                        t.setArtist("");
                        t.setDuration(0);
                        t.setFormat("");
                        t.setHasLyric(false);
                        t.setFileSize(0L);
                        t.setMediaUrl(dto.getMediaUrl());
                        t.setUrlExpiresAt(dto.getExpiresAt());
                        trackMapper.insert(t);
                    } else {
                        t.setMediaUrl(dto.getMediaUrl());
                        t.setUrlExpiresAt(dto.getExpiresAt());
                        // 续期成功即恢复健康状态：清除历史失败原因与不可播标记，
                        // 否则 SOURCE_GONE / MEDIA_UNAVAILABLE 会残留，令该曲目被永久跳过。
                        t.setPlayable(1);
                        t.setLastError(null);
                        trackMapper.updateById(t);
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
            int ok = refreshSucceeded.get(), total = ids.size(), gone = refreshSourceGone.get();
            if (total > 0 && ok == 0 && gone < total) {
                // 全军覆没通常意味着蓝奏云改版/会话异常，必须显式告警而非静默"完成"
                log.error("TrackCacheService: 直链刷新全部失败 0/{}，播放链接将无法续期，请检查蓝奏云取链逻辑！", total);
            } else if (total > 0 && ok == 0) {
                log.warn("TrackCacheService: 本轮 {} 首均为源文件已不可用（分享取消等），已标记跳过", total);
            } else {
                log.info("TrackCacheService: 刷新完成 成功 {}/{}（已处理 {}）", ok, total, refreshCompleted.get());
            }
        } finally { refreshing = false; }
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

    @Async
    public void manualRefresh() {
        List<String> ids = trackService.getAllTrackIds();
        refreshAll(ids);
    }

    public boolean isInitialized() { return initialized; }
    public boolean isRefreshing() { return refreshing; }
    public Map<String, Object> getStatus() {
        return Map.of("total", refreshTotal.get(), "completed", refreshCompleted.get(), "inProgress", refreshing, "initialized", initialized);
    }
}
