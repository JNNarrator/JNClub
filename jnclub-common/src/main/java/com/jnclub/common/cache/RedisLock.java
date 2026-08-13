package com.jnclub.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 轻量分布式锁（SET NX PX + Lua 释放校验）。
 *
 * <p>不引入 Redisson（其 Jackson 2.x 依赖与 Spring Boot 4 冲突风险高）。
 * 锁自动过期（TTL），释放时校验持有者 token，防止误删他人锁；超时自动释放，无死锁残留。</p>
 */
@Slf4j
@Component
public class RedisLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 尝试加锁。成功返回 token（供 {@link #unlock} 使用），失败返回 null。 */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (Exception e) {
            log.warn("Redis 加锁失败: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 释放锁。仅当 token 匹配才删除，防止误删他人锁。 */
    public void unlock(String key, String token) {
        if (token == null) return;
        try {
            redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception e) {
            log.warn("Redis 解锁失败: key={}, err={}", key, e.getMessage());
        }
    }
}
