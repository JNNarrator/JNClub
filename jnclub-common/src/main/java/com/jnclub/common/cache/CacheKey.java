package com.jnclub.common.cache;

/**
 * Redis Key 集中定义 —— 统一前缀规范，便于监控与按前缀批量失效。
 *
 * <pre>
 * jnclub:cache:{domain}:{dimensions}   # 业务缓存
 * jnclub:rate:{domain}:{scope}         # 限流计数
 * jnclub:lock:{domain}:{scope}         # 分布式锁
 * </pre>
 */
public final class CacheKey {

    private static final String CACHE = "jnclub:cache:";
    private static final String RATE = "jnclub:rate:";
    private static final String LOCK = "jnclub:lock:";

    private CacheKey() {
    }

    // ==================== 目录树 ====================
    public static String dir(String userId, Integer type) {
        return CACHE + "dir:" + userId + ":" + (type == null ? "all" : type);
    }

    public static String dirPrefix(String userId) {
        return CACHE + "dir:" + userId + ":";
    }

    // ==================== 收藏 ====================
    public static String bookmark(String userId, Long directoryId) {
        return CACHE + "bm:" + userId + ":" + directoryId;
    }

    public static String bookmarkPrefix(String userId) {
        return CACHE + "bm:" + userId + ":";
    }

    // ==================== 便签 ====================
    public static String note(String userId, Long directoryId) {
        return CACHE + "note:" + userId + ":" + directoryId;
    }

    public static String notePrefix(String userId) {
        return CACHE + "note:" + userId + ":";
    }

    // ==================== 标签 ====================
    public static String tag(String userId, String refType) {
        return CACHE + "tag:" + userId + ":" + (refType == null ? "all" : refType);
    }

    public static String tagPrefix(String userId) {
        return CACHE + "tag:" + userId + ":";
    }

    // ==================== 用户偏好 ====================
    public static String pref(String userId) {
        return CACHE + "pref:" + userId;
    }

    // ==================== 音乐批量直链 ====================
    public static String musicUrls() {
        return CACHE + "music:urls";
    }

    // ==================== 限流 ====================
    public static String rate(String domain, String scope) {
        return RATE + domain + ":" + scope;
    }

    // ==================== 分布式锁 ====================
    public static String lock(String domain, String scope) {
        return LOCK + domain + ":" + scope;
    }
}
