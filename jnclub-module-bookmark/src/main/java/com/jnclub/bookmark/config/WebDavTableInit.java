package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建 t_webdav_server 表（避免人工执行迁移脚本）
 * WebDAV 站点管理：URL + 独立账号密码 + 文件管理入口
 */
@Slf4j
@Component
public class WebDavTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public WebDavTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_webdav_server (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    name VARCHAR(200) DEFAULT '' COMMENT '站点名称',
                    url VARCHAR(1024) NOT NULL COMMENT 'WebDAV 服务地址',
                    username VARCHAR(300) DEFAULT '' COMMENT '登录账号，可为空(匿名)',
                    password VARCHAR(1024) DEFAULT NULL COMMENT '密码(AES密文)',
                    notes VARCHAR(1000) DEFAULT '' COMMENT '备注',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebDAV 站点管理表'
                """);
        } catch (Exception e) {
            log.warn("t_webdav_server 建表失败（不影响启动）: {}", e.getMessage());
        }
    }
}
