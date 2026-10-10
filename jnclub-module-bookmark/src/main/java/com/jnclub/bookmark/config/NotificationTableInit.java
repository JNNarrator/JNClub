package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建站内提醒表 t_notification（避免人工执行迁移脚本）
 */
@Slf4j
@Component
public class NotificationTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public NotificationTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable("t_notification", """
                CREATE TABLE IF NOT EXISTS t_notification (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    type VARCHAR(30) NOT NULL COMMENT '类型：TODO_REMIND/OTHER',
                    title VARCHAR(300) NOT NULL COMMENT '标题',
                    content VARCHAR(1000) DEFAULT '' COMMENT '内容',
                    ref_type VARCHAR(30) DEFAULT NULL COMMENT '关联类型：todo',
                    ref_id BIGINT DEFAULT NULL COMMENT '关联ID',
                    read_flag TINYINT DEFAULT 0 NOT NULL COMMENT '已读：0未读 1已读',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    INDEX idx_user_read (user_id, read_flag),
                    INDEX idx_ref (ref_type, ref_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内提醒表'
                """);

        ensureIndex("t_notification", "idx_user_read", "user_id, read_flag, create_time");
    }

    private void ensureTable(String table, String createSql) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    """, Integer.class, table);
            if (count == null || count == 0) {
                jdbcTemplate.execute(createSql);
                log.info("已创建表 {}", table);
            }
        } catch (Exception e) {
            log.warn("{} 建表失败（不影响启动）: {}", table, e.getMessage());
        }
    }

    private void ensureIndex(String table, String indexName, String columnList) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                    """, Integer.class, table, indexName);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + indexName + " (" + columnList + ")");
                log.info("{} 表已新增索引 {}", table, indexName);
            }
        } catch (Exception e) {
            log.warn("{} 表索引 {} 检查/迁移失败（不影响启动）: {}", table, indexName, e.getMessage());
        }
    }
}
