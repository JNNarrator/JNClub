package com.jnclub.music.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 分档配置各缓存容量。
     * <p>原先所有缓存共用 {@code maximumSize(200)}：曲库稍大时，一次列表请求写入的
     * mediaUrls 条目就会把其它曲目的直链挤出去，导致播放时反复回源蓝奏云（慢且易被反爬）。
     * 现按访问模式分档：直链/歌词按曲目数量级给足，目录与摘要列表是单条目大对象、不需要大容量。</p>
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // 兜底默认值：未单独声明的缓存沿用
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(45))
                .recordStats());
        // 目录树 / 歌曲摘要：单 key（"all"）持有整份列表，容量不需要大，但要长 TTL
        manager.registerCustomCache("songFolders", cache(4, Duration.ofMinutes(45)));
        manager.registerCustomCache("trackSummaries", cache(4, Duration.ofMinutes(45)));
        // 直链：每个 trackId 一条，容量需覆盖全曲库，否则相互驱逐导致反复回源
        manager.registerCustomCache("mediaUrls", cache(5000, Duration.ofMinutes(45)));
        // 歌词：单曲体积较大，给足容量并适当延长 TTL（歌词内容基本不变）
        manager.registerCustomCache("lyrics", cache(2000, Duration.ofHours(12)));
        return manager;
    }

    /**
     * 构建独立配置的 Caffeine 缓存实例（registerCustomCache 需要实例，而非构建器）。
     * <p>{@code recordStats()} 让 Micrometer 能采集命中率等指标：既消除启动时的
     * 「is not recording statistics」告警，也让「直链是否还在被相互驱逐」变得可观测
     * ——这正是本次容量分档要解决的问题。</p>
     */
    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> cache(long maximumSize, Duration ttl) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }
}
