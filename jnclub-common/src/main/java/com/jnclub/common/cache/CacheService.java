package com.jnclub.common.cache;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 业务缓存服务（Cache-Aside 旁路缓存）。
 *
 * <p>设计要点（清理与失效）：</p>
 * <ul>
 *   <li>所有 key 必带 TTL，无永久缓存 key；写后主动删优先，TTL 兜底。</li>
 *   <li>空结果写空集合 + 短 TTL（防穿透）；Redis 异常一律降级直查 DB，绝不影响主流程。</li>
 *   <li>序列化用 hutool JSON，仅操作 String，规避 Spring Boot 4 的 Jackson 2/3 冲突。</li>
 * </ul>
 *
 * <p>约定：{@link #getList}/{@link #getMap} 返回 {@code null} 表示「未命中，需回源」；
 * 返回空集合（非 null）表示「命中且为空」，调用方直接返回，避免反复穿透 DB。</p>
 */
@Slf4j
@Service
public class CacheService {

    /** 默认业务缓存 TTL：10 分钟（写后主动删优先，此处仅兜底） */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    /** 空结果标记 TTL：60 秒（防穿透） */
    public static final Duration NULL_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public CacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ==================== 底层 String 操作 ====================

    public String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get 失败，降级直查: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    public void set(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis set 失败: key={}, err={}", key, e.getMessage());
        }
    }

    /** SET NX：仅当 key 不存在时写入（防击穿互斥、分布式锁基础）。返回 true 表示写入成功。 */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, value, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("Redis setIfAbsent 失败: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete 失败: key={}, err={}", key, e.getMessage());
        }
    }

    /** 按前缀批量删除（SCAN + DEL，避免 KEYS 阻塞）。个人项目 key 极少，性能无虞。 */
    public void evictByPrefix(String prefix) {
        try (Cursor<String> cursor = redis.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        } catch (Exception e) {
            log.warn("Redis evictByPrefix 失败: prefix={}, err={}", prefix, e.getMessage());
        }
    }

    // ==================== 泛型便捷 ====================

    /** 读列表。null=未命中（需回源）；空 List=命中且为空。 */
    public <T> List<T> getList(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null) return null;
        try {
            return JSONUtil.toList(json, clazz);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，降级直查: key={}, err={}", key, e.getMessage());
            evict(key);
            return null;
        }
    }

    /** 写列表。空列表用短 TTL（防穿透）。 */
    public <T> void setList(String key, List<T> list, Duration ttl) {
        if (list == null) return;
        set(key, JSONUtil.toJsonStr(list), list.isEmpty() ? NULL_TTL : ttl);
    }

    /** 读 Map（JSONObject）。null=未命中。 */
    public Map<String, Object> getMap(String key) {
        String json = get(key);
        if (json == null) return null;
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，降级直查: key={}, err={}", key, e.getMessage());
            evict(key);
            return null;
        }
    }

    /** 写 Map。空 Map 用短 TTL（防穿透）。 */
    public void setMap(String key, Map<?, ?> map, Duration ttl) {
        if (map == null) return;
        set(key, JSONUtil.toJsonStr(map), map.isEmpty() ? NULL_TTL : ttl);
    }
}
