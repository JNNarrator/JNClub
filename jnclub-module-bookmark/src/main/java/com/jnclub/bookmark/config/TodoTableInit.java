package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建待办相关表并补齐新列（避免人工执行迁移脚本）
 * - t_todo：待办主表
 * - t_todo_item：待办子任务（清单）
 * - t_notification：站内提醒
 */
@Slf4j
@Component
public class TodoTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public TodoTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_todo (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    title VARCHAR(500) NOT NULL COMMENT '待办标题',
                    note VARCHAR(2000) DEFAULT '' COMMENT '备注',
                    priority TINYINT DEFAULT 0 NOT NULL COMMENT '优先级：0低 1中 2高',
                    due_date DATE DEFAULT NULL COMMENT '截止日期',
                    due_time TIME DEFAULT NULL COMMENT '截止时间',
                    remind_at DATETIME DEFAULT NULL COMMENT '提醒时间',
                    remind_notified TINYINT DEFAULT 0 NOT NULL COMMENT '提醒已通知：0否 1是',
                    recurrence VARCHAR(20) DEFAULT NULL COMMENT '重复规则：DAILY/WEEKLY/MONTHLY/YEARLY',
                    recurrence_interval INT DEFAULT 1 NOT NULL COMMENT '重复间隔',
                    completed TINYINT DEFAULT 0 NOT NULL COMMENT '完成标记：0未完成 1已完成',
                    completed_at DATETIME DEFAULT NULL COMMENT '完成时间',
                    sort_order INT DEFAULT 0 NOT NULL COMMENT '排序',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除：0正常 1已删除',
                    INDEX idx_user (user_id),
                    INDEX idx_user_due (user_id, due_date),
                    INDEX idx_remind (remind_at, remind_notified)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办清单表'
                """);
        } catch (Exception e) {
            log.warn("t_todo 建表失败（不影响启动）: {}", e.getMessage());
        }

        // 旧表补齐新列（幂等）
        ensureColumn("t_todo", "due_time",
                "ALTER TABLE t_todo ADD COLUMN due_time TIME DEFAULT NULL COMMENT '截止时间' AFTER due_date");
        ensureColumn("t_todo", "remind_at",
                "ALTER TABLE t_todo ADD COLUMN remind_at DATETIME DEFAULT NULL COMMENT '提醒时间' AFTER due_time");
        ensureColumn("t_todo", "remind_notified",
                "ALTER TABLE t_todo ADD COLUMN remind_notified TINYINT DEFAULT 0 NOT NULL COMMENT '提醒已通知：0否 1是' AFTER remind_at");
        ensureColumn("t_todo", "recurrence",
                "ALTER TABLE t_todo ADD COLUMN recurrence VARCHAR(20) DEFAULT NULL COMMENT '重复规则：DAILY/WEEKLY/MONTHLY/YEARLY' AFTER remind_notified");
        ensureColumn("t_todo", "recurrence_interval",
                "ALTER TABLE t_todo ADD COLUMN recurrence_interval INT DEFAULT 1 NOT NULL COMMENT '重复间隔' AFTER recurrence");

        // 子任务表
        ensureTable("t_todo_item", """
                CREATE TABLE IF NOT EXISTS t_todo_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    todo_id BIGINT NOT NULL COMMENT '待办ID',
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    title VARCHAR(500) NOT NULL COMMENT '子任务标题',
                    completed TINYINT DEFAULT 0 NOT NULL COMMENT '完成标记：0未完成 1已完成',
                    sort_order INT DEFAULT 0 NOT NULL COMMENT '排序',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除：0正常 1已删除',
                    INDEX idx_todo (todo_id),
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办子任务表'
                """);

        // 站内提醒表
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

        // 服务端搜索历史表
        ensureTable("t_search_history", """
                CREATE TABLE IF NOT EXISTS t_search_history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    keyword VARCHAR(200) NOT NULL COMMENT '搜索关键词',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    INDEX idx_user_time (user_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表'
                """);

        // 稳定与性能：补齐高频查询索引（幂等）
        ensureIndex("t_todo", "idx_user_due", "user_id, due_date");
        ensureIndex("t_todo", "idx_user_completed", "user_id, completed, due_date");
        ensureIndex("t_todo", "idx_remind", "remind_at, remind_notified");
        ensureIndex("t_bookmark", "idx_user_updated", "user_id, update_time");
        ensureIndex("t_feed_item", "idx_feed_created", "feed_id, create_time");
        ensureIndex("t_notification", "idx_user_read", "user_id, read_flag, create_time");
        ensureIndex("t_search_history", "idx_user_time", "user_id, create_time");
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, table, column);
            if (count == null || count == 0) {
                jdbcTemplate.execute(alterSql);
                log.info("{} 表已新增列 {}", table, column);
            }
        } catch (Exception e) {
            log.warn("{} 表列 {} 检查/迁移失败（不影响启动）: {}", table, column, e.getMessage());
        }
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