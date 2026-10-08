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

    private final TrackMapper trackMapper;
    private final TrackService trackService;
    private final CacheManager cacheManager;
    private final RedisLock redisLock;

    private final AtomicInteger refreshTotal = new AtomicInteger(0);
    private final AtomicInteger refreshCompleted = new AtomicInteger(0);
    /** 真正刷新成功的数量：与 refreshCompleted（已处理数）区分，避免全部失败时日志仍显示 "完成 N/N"。 */
    private final AtomicInteger refreshSucceeded = new AtomicInteger(0);
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
        // 逐首刷新过期/缺失的 URL，失败时保留旧值，避免蓝奏云失效时清空所有链接
        List<String> expired = new ArrayList<>();
        for (Track t : all) {
            if (t.getMediaUrl() == null || t.getUrlExpiresAt() == null || t.getUrlExpiresAt().isBefore(OffsetDateTime.now())) {
                expired.add(t.getTrackId());
            }
        }
        if (!expired.isEmpty()) {
            refreshAll(expired);
        }
    }

    @Scheduled(cron = "0 0 */2 * * ?")
    public void scheduledRefresh() {
        String lockKey = CacheKey.lock("scheduled", "track-refresh");
        String token = redisLock.tryLock(lockKey, SCHEDULED_LOCK_TTL);
        if (token == null) {
            log.info("TrackCacheService: 直链刷新已被其他实例执行，跳过");
            return;
        }
        try {
            List<String> ids = trackService.getAllTrackIds();
            if (ids.isEmpty()) {
                // 曲目列表依赖蓝奏云目录接口：为空说明目录/会话异常，此时刷新被静默跳过，
                // 会导致所有直链在 45 分钟后集体失效且无人知晓，必须显式告警。
                log.error("TrackCacheService: 未获取到任何曲目（蓝奏云目录/会话可能异常），本次直链刷新被跳过！");
                return;
            }
            refreshAll(ids);
        } finally {
            redisLock.unlock(lockKey, token);
        }
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
                        trackMapper.updateById(t);
                    }
                    if (cache != null) cache.put(id, dto);
                    refreshSucceeded.incrementAndGet();
                } catch (Exception e) {
                    log.warn("TrackCacheService: {} 刷新失败: {}，保留旧值", id, e.getMessage());
                } finally {
                    refreshCompleted.incrementAndGet();
                }
            }
            int ok = refreshSucceeded.get(), total = ids.size();
            if (total > 0 && ok == 0) {
                // 全军覆没通常意味着蓝奏云改版/会话异常，必须显式告警而非静默"完成"
                log.error("TrackCacheService: 直链刷新全部失败 0/{}，播放链接将无法续期，请检查蓝奏云取链逻辑！", total);
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
