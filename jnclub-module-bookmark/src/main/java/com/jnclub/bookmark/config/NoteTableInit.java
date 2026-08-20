package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等迁移便签相关表结构（避免人工执行迁移脚本）：
 * 1. t_note 增加 pinned（置顶）/ archived（归档）列（列不存在才 ALTER）
 * 2. 幂等创建 t_note_version（便签历史版本快照表）
 */
@Slf4j
@Component
public class NoteTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public NoteTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("t_note", "pinned",
                "ALTER TABLE t_note ADD COLUMN pinned TINYINT DEFAULT 0 NOT NULL COMMENT '置顶标记：0否 1是' AFTER sort_order");
        ensureColumn("t_note", "archived",
                "ALTER TABLE t_note ADD COLUMN archived TINYINT DEFAULT 0 NOT NULL COMMENT '归档标记：0正常 1已归档' AFTER pinned");
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_note_version (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    note_id BIGINT NOT NULL COMMENT '便签ID',
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    title VARCHAR(200) DEFAULT '' COMMENT '快照标题',
                    content MEDIUMTEXT COMMENT '快照内容(Markdown原文)',
                    version_no INT NOT NULL DEFAULT 1 COMMENT '版本号(自增)',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
                    INDEX idx_note (note_id),
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='便签历史版本表'
                """);
        } catch (Exception e) {
            log.warn("t_note_version 建表失败（不影响启动）: {}", e.getMessage());
        }
    }

    /** 检查列是否存在，不存在则执行 ALTER（幂等） */
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
}
