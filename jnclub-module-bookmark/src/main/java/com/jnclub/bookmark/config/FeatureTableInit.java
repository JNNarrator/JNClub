package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FeatureTableInit — 2026-09 批次功能的启动幂等迁移
 * （沿用 NoteTableInit 模式：列不存在才 ALTER / 表不存在才 CREATE，零人工迁移）
 *
 * 本批变更：
 * 1. t_share 增加 visit_count（访问统计 PV）/ last_visit_at（最近访问时间）
 */
@Slf4j
@Component
public class FeatureTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public FeatureTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 分享统计（⑤）
        ensureColumn("t_share", "visit_count",
                "ALTER TABLE t_share ADD COLUMN visit_count INT DEFAULT 0 NOT NULL COMMENT '访问统计(PV)' AFTER expires_at");
        ensureColumn("t_share", "last_visit_at",
                "ALTER TABLE t_share ADD COLUMN last_visit_at DATETIME DEFAULT NULL COMMENT '最近访问时间' AFTER visit_count");
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

    /** 检查表是否存在，不存在则执行 CREATE（幂等） */
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
}
