package com.jnclub.wechat.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * context_token 的持久化缓存：内存命中优先，未命中回落数据库，写入时穿透落库。
 *
 * <p>原实现（clawbot 的 {@code ContextTokenCache}）只有一张 ConcurrentHashMap，
 * 进程重启即清空。而 iLink 发送消息必须以"该对话最近一次的 context_token"作为凭据，
 * 一旦为空，{@code sendTextMessage} 直接抛 IllegalStateException。
 * 结果就是：每次部署后，"电脑复制 → 手机收到"这条链路静默失效，直到用户
 * 碰巧又给 bot 发一条消息才恢复——这种"偶尔不好使"的故障最难排查。
 *
 * <p>这里把写入穿透到数据库，并在启动时预热，使重启后立刻可以主动推送。
 */
@Slf4j
@Component
public class ContextTokenStore {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ContextTokenStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 启动预热：把库里已有的 token 读回内存。 */
    public void warmUp() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT peer_id, context_token FROM t_wechat_context_token");
            for (Map<String, Object> row : rows) {
                Object peer = row.get("peer_id");
                Object token = row.get("context_token");
                if (peer != null && token != null) {
                    cache.put(String.valueOf(peer), String.valueOf(token));
                }
            }
            log.info("context_token 预热完成，共 {} 条", cache.size());
        } catch (Exception e) {
            log.warn("context_token 预热失败（表可能尚未建立）：{}", e.getMessage());
        }
    }

    /** 写入并落库。token 没变时不写库，避免每条消息都产生一次 UPDATE。 */
    public void put(String userId, String peerId, String contextToken) {
        if (peerId == null || contextToken == null || contextToken.isEmpty()) {
            return;
        }
        String previous = cache.put(peerId, contextToken);
        if (contextToken.equals(previous)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_wechat_context_token (user_id, peer_id, context_token, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE context_token = VALUES(context_token), updated_at = VALUES(updated_at)
                    """, userId, peerId, contextToken, System.currentTimeMillis());
        } catch (Exception e) {
            // 落库失败不影响本次发送：内存里已经有了。
            log.warn("context_token 落库失败 peer={}：{}", peerId, e.getMessage());
        }
    }

    public String get(String peerId) {
        return cache.get(peerId);
    }

    /** 发送前取凭据；没有则明确返回 null，由调用方决定降级策略。 */
    public String getOrNull(String peerId) {
        return cache.get(peerId);
    }

    public int size() {
        return cache.size();
    }

    public void remove(String peerId) {
        cache.remove(peerId);
        try {
            jdbcTemplate.update("DELETE FROM t_wechat_context_token WHERE peer_id = ?", peerId);
        } catch (Exception e) {
            log.warn("context_token 删除失败：{}", e.getMessage());
        }
    }

    public void clear() {
        cache.clear();
        try {
            jdbcTemplate.update("DELETE FROM t_wechat_context_token");
        } catch (Exception e) {
            log.warn("context_token 清空失败：{}", e.getMessage());
        }
    }
}
