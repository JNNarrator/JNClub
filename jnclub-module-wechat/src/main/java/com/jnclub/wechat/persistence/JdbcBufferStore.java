package com.jnclub.wechat.persistence;

import com.jnclub.wechat.ilink.persistence.BufferStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 同步游标（get_updates_buf）的数据库存储。
 *
 * <p>原实现写在本地文件里，对单机部署够用，但容器/多实例下文件不共享，
 * 重启换实例就会从头拉取或丢增量。放进数据库与其它状态保持一致。
 *
 * <p>并发语义沿用原实现：getBuffer 读内存，updateBuffer 先改内存再落库；
 * 用 synchronized 保证同一实例内的顺序。
 */
@Slf4j
public class JdbcBufferStore implements BufferStore {

    private final JdbcTemplate jdbcTemplate;
    private final String userId;

    private String buffer = "";

    public JdbcBufferStore(JdbcTemplate jdbcTemplate, String userId) {
        this.jdbcTemplate = jdbcTemplate;
        this.userId = userId;
        this.buffer = load();
    }

    @Override
    public synchronized String getBuffer() {
        return buffer;
    }

    @Override
    public synchronized void updateBuffer(String newBuffer) {
        if (newBuffer == null || newBuffer.isEmpty()) {
            return;
        }
        this.buffer = newBuffer;
        save();
    }

    @Override
    public synchronized void clear() {
        this.buffer = "";
        try {
            jdbcTemplate.update("DELETE FROM t_wechat_config WHERE user_id = ?", "sync_buf:" + userId);
        } catch (Exception e) {
            log.warn("清空同步游标失败：{}", e.getMessage());
        }
    }

    @Override
    public String load() {
        try {
            var rows = jdbcTemplate.queryForList(
                    "SELECT whitelist FROM t_wechat_config WHERE user_id = ?", "sync_buf:" + userId);
            if (!rows.isEmpty()) {
                Object v = rows.get(0).get("whitelist");
                return v == null ? "" : String.valueOf(v);
            }
        } catch (Exception e) {
            log.warn("读取同步游标失败：{}", e.getMessage());
        }
        return "";
    }

    @Override
    public void save() {
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_wechat_config (user_id, enabled, clipboard_bridge, whitelist, notify_on_copy, updated_at)
                    VALUES (?, 0, 0, ?, 0, ?)
                    ON DUPLICATE KEY UPDATE whitelist = VALUES(whitelist), updated_at = VALUES(updated_at)
                    """, "sync_buf:" + userId, buffer, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("保存同步游标失败：{}", e.getMessage());
        }
    }
}
