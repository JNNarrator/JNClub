package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等迁移收藏表结构：t_bookmark 增加失效检测字段
 * check_status: 0=未检测 1=正常 2=失效；checked_at: 最近检测时间
 */
@Slf4j
@Component
public class BookmarkTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public BookmarkTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("t_bookmark", "check_status",
                "ALTER TABLE t_bookmark ADD COLUMN check_status TINYINT DEFAULT 0 NOT NULL COMMENT '失效检测状态：0未检测 1正常 2失效' AFTER deleted");
        ensureColumn("t_bookmark", "checked_at",
                "ALTER TABLE t_bookmark ADD COLUMN checked_at DATETIME DEFAULT NULL COMMENT '最近失效检测时间' AFTER check_status");
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
