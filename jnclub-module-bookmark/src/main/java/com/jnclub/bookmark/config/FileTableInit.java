package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等迁移云盘文件表结构：t_file 增加 content_hash 列（重复文件检测用）
 */
@Slf4j
@Component
public class FileTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public FileTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_file' AND COLUMN_NAME = 'content_hash'",
                    Integer.class);
            if (cnt != null && cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE t_file "
                        + "ADD COLUMN content_hash VARCHAR(64) DEFAULT NULL COMMENT '文件内容MD5(查重用)' AFTER stored_key");
                log.info("t_file 已增加 content_hash 列");
            }
        } catch (Exception e) {
            log.warn("t_file content_hash 迁移失败（不影响启动）: {}", e.getMessage());
        }
    }
}
