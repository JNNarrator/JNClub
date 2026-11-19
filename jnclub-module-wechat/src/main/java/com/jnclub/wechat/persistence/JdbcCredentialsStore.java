package com.jnclub.wechat.persistence;

import com.jnclub.wechat.ilink.model.auth.Credentials;
import com.jnclub.wechat.ilink.persistence.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * iLink 登录凭据的数据库存储。
 *
 * <p>凭据是扫码登录后拿到的 bot token，是整个通道的根凭据：
 * 存得住就不必每次重启都重新扫码。原实现写本地文件（account.json），
 * 这里改为落库，与 JNClub 其它模块的持久化方式一致。
 */
@Slf4j
public class JdbcCredentialsStore implements Store {

    private final JdbcTemplate jdbcTemplate;
    private final String userId;

    private Long id;

    public JdbcCredentialsStore(JdbcTemplate jdbcTemplate, String userId) {
        this.jdbcTemplate = jdbcTemplate;
        this.userId = userId;
    }

    @Override
    public void save(Credentials credentials) {
        if (credentials == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_wechat_credentials
                        (user_id, bot_token, base_url, account_id, wechat_user_id, saved_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        bot_token = VALUES(bot_token), base_url = VALUES(base_url),
                        account_id = VALUES(account_id), wechat_user_id = VALUES(wechat_user_id),
                        saved_at = VALUES(saved_at), updated_at = VALUES(updated_at)
                    """,
                    userId, credentials.getToken(), credentials.getBaseUrl(),
                    credentials.getAccountId(), credentials.getUserId(),
                    credentials.getSavedAt() == null ? null : credentials.getSavedAt().toEpochMilli(),
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.error("保存微信凭据失败：{}", e.getMessage());
        }
    }

    @Override
    public Credentials load() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM t_wechat_credentials WHERE user_id = ?", userId);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            Credentials c = new Credentials();
            c.setToken(str(row.get("bot_token")));
            c.setBaseUrl(str(row.get("base_url")));
            c.setAccountId(str(row.get("account_id")));
            c.setUserId(str(row.get("wechat_user_id")));
            Object savedAt = row.get("saved_at");
            if (savedAt instanceof Number n) {
                c.setSavedAt(java.time.Instant.ofEpochMilli(n.longValue()));
            }
            Object pk = row.get("id");
            if (pk instanceof Number n) {
                this.id = n.longValue();
            }
            return c.getToken() == null ? null : c;
        } catch (Exception e) {
            log.warn("读取微信凭据失败：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public void clear() {
        try {
            jdbcTemplate.update("DELETE FROM t_wechat_credentials WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("清除微信凭据失败：{}", e.getMessage());
        }
    }

    @Override
    public boolean exists() {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_wechat_credentials WHERE user_id = ?", Integer.class, userId);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
