package com.jnclub.wechat.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * context_token 的持久化缓存：内存命中优先，未命中回落数据库，写入时穿透落库。
 *
 * <h2>为什么一定要落库</h2>
 * iLink 发送消息必须以"该对话最近一次的 context_token"作为凭据，一旦为空，
 * {@code sendTextMessage} 直接抛异常。而原实现把它放在纯内存 Map 里，
 * 进程重启即清空——于是"电脑复制 → 手机收到"会在每次部署后静默失效，
 * 必须等用户重新给 bot 发一条消息才能恢复。
 *
 * <h2>为什么内存键是 (userId, peerId) 而不是 peerId</h2>
 * 多租户下同一个微信用户可能分别给两个账号的 bot 发过消息，
 * 两个 bot 各自持有不同的 context_token。只按 peerId 缓存会让它们互相覆盖，
 * 表现为"消息发出去但收不到"，且没有任何报错。
 */
@Slf4j
@Component
public class ContextTokenStore {

    private static final String SEP = "\u0001";

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ContextTokenStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static String key(String userId, String peerId) {
        return userId + SEP + peerId;
    }

    /** 启动预热：把库里已有的 token 读回内存。 */
    public void warmUp() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id, peer_id, context_token FROM t_wechat_context_token");
            for (Map<String, Object> row : rows) {
                Object user = row.get("user_id");
                Object peer = row.get("peer_id");
                Object token = row.get("context_token");
                if (user != null && peer != null && token != null) {
                    cache.put(key(String.valueOf(user), String.valueOf(peer)), String.valueOf(token));
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
        String previous = cache.put(key(userId, peerId), contextToken);
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
            log.warn("context_token 落库失败 user={} peer={}：{}", userId, peerId, e.getMessage());
        }
    }

    /**
     * 取某个对话的 token。
     *
     * <p>只接受带 userId 的调用——多租户下"这个 peer 的 token"不唯一，
     * 必须指明是谁的对话，否则会拿到别人的 token。
     */
    public String get(String userId, String peerId) {
        String value = cache.get(key(userId, peerId));
        if (value != null) {
            return value;
        }
        String loaded = loadFromDb(userId, peerId);
        if (loaded != null) {
            cache.put(key(userId, peerId), loaded);
        }
        return loaded;
    }

    private String loadFromDb(String userId, String peerId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT context_token FROM t_wechat_context_token WHERE user_id = ? AND peer_id = ?",
                    userId, peerId);
            if (rows.isEmpty()) {
                return null;
            }
            Object token = rows.get(0).get("context_token");
            return token == null ? null : String.valueOf(token);
        } catch (Exception e) {
            return null;
        }
    }

    /** 某用户已知的对话方列表，用于主动推送。 */
    public List<String> peersOf(String userId) {
        List<String> peers = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT peer_id FROM t_wechat_context_token WHERE user_id = ?", userId);
            for (Map<String, Object> row : rows) {
                Object value = row.get("peer_id");
                if (value != null) {
                    peers.add(String.valueOf(value));
                }
            }
        } catch (Exception e) {
            log.warn("读取对话方失败 user={}：{}", userId, e.getMessage());
        }
        return peers;
    }

    public int size() {
        return cache.size();
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
