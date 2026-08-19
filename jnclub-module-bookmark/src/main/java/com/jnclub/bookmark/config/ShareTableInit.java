package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建 t_share 表（避免人工执行迁移脚本）
 */
@Slf4j
@Component
public class ShareTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ShareTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_share (
                    token VARCHAR(32) PRIMARY KEY COMMENT '分享 token',
                    ref_type VARCHAR(16) NOT NULL COMMENT 'note/bookmark/file',
                    ref_id BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    password_hash VARCHAR(128) DEFAULT NULL COMMENT '访问密码hash，空=免密',
                    expires_at DATETIME DEFAULT NULL COMMENT '过期时间，null=永不过期',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_share_ref (ref_type, ref_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公开只读分享表'
                """);
        } catch (Exception e) {
            log.warn("t_share 建表失败（不影响启动）: {}", e.getMessage());
        }
    }
}
