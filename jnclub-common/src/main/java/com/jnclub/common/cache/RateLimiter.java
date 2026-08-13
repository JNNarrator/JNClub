package com.jnclub.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * 固定窗口限流器（Lua 原子 INCR + EXPIRE，计数随窗口自动过期，无残留）。
 */
@Slf4j
@Component
public class RateLimiter {

    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]); if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; return c",
            Long.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 固定窗口限流：返回 true 表示放行，false 表示超限。Redis 异常时降级放行（不影响业务）。 */
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redis.execute(INCR_SCRIPT, Collections.singletonList(key),
                    String.valueOf(window.getSeconds()));
            return count != null && count <= limit;
        } catch (Exception e) {
            log.warn("Redis 限流失败，降级放行: key={}, err={}", key, e.getMessage());
            return true;
        }
    }
}
