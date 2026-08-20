package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建待办表 t_todo（避免人工执行迁移脚本）
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
                    completed TINYINT DEFAULT 0 NOT NULL COMMENT '完成标记：0未完成 1已完成',
                    completed_at DATETIME DEFAULT NULL COMMENT '完成时间',
                    sort_order INT DEFAULT 0 NOT NULL COMMENT '排序',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除：0正常 1已删除',
                    INDEX idx_user (user_id),
                    INDEX idx_user_due (user_id, due_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办清单表'
                """);
        } catch (Exception e) {
            log.warn("t_todo 建表失败（不影响启动）: {}", e.getMessage());
        }
    }
}
