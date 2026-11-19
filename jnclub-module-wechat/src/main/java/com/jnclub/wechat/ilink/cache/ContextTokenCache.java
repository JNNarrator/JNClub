package com.jnclub.wechat.ilink.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context Token 缓存。
 *
 * <p>保持原 SDK 的单参 API 不变——{@code MessageReceiverService} 收到消息时
 * 调 {@code put(peerId, token)}，{@code MessageSenderService} 发送时调
 * {@code getOrThrow(peerId)}，两处都不用改。
 *
 * <p>区别在于：可以挂一个 {@link Persistence} 委托，把写入穿透到数据库。
 * 不挂时行为与原实现完全一致（纯内存），因此 iLink 仍可独立使用。
 */
public class ContextTokenCache {

    /** 持久化委托；为空表示纯内存模式。 */
    public interface Persistence {
        void save(String peerId, String contextToken);

        String load(String peerId);
    }

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Persistence persistence;

    public ContextTokenCache() {
        this(null);
    }

    public ContextTokenCache(Persistence persistence) {
        this.persistence = persistence;
    }

    /**
     * 缓存用户的 context token
     */
    public void put(String userId, String contextToken) {
        if (userId == null || contextToken == null) {
            return;
        }
        String previous = cache.put(userId, contextToken);
        // token 未变化就不重复落库：context_token 会随每轮 getUpdates 刷新，
        // 但不该每条消息都写一次库。
        if (persistence != null && !contextToken.equals(previous)) {
            persistence.save(userId, contextToken);
        }
    }

    /**
     * 获取用户的 context token
     */
    public String get(String userId) {
        String token = cache.get(userId);
        if (token == null && persistence != null) {
            // 内存未命中时回落数据库：这是服务重启后仍能主动推送的关键。
            token = persistence.load(userId);
            if (token != null) {
                cache.put(userId, token);
            }
        }
        return token;
    }

    /**
     * 获取用户的 context token，如果不存在则抛出异常
     */
    public String getOrThrow(String userId) {
        String token = get(userId);
        if (token == null) {
            throw new IllegalStateException("No context token for user: " + userId);
        }
        return token;
    }

    /**
     * 删除用户的 context token
     */
    public void remove(String userId) {
        cache.remove(userId);
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }
}
