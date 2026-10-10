package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建服务端搜索历史表 t_search_history（避免人工执行迁移脚本）
 */
@Slf4j
@Component
public class SearchHistoryTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SearchHistoryTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_search_history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    keyword VARCHAR(200) NOT NULL COMMENT '搜索关键词',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    INDEX idx_user_time (user_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表'
                """);
        } catch (Exception e) {
            log.warn("t_search_history 建表失败（不影响启动）: {}", e.getMessage());
        }
    }
}
