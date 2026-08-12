package com.jnclub.sso;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Sa-Token 会话持久化 DAO 的 Caffeine 读缓存配置。
 * <p>
 * 仅作热读缓存，兜底数据在 MySQL；容量与 TTL 对齐 JN_SSO 服务端配置。
 */
@Configuration
public class SaTokenCacheConfig {

    @Bean("saTokenCache")
    public Cache<String, Object> saTokenCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofDays(7))
                .build();
    }
}