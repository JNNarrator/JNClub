package com.jnclub.bookmark.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建 t_vault_totp 表（避免人工执行迁移脚本）
 */
@Slf4j
@Component
public class VaultTotpTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public VaultTotpTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_vault_totp (
                    vault_id BIGINT PRIMARY KEY COMMENT '密码库条目ID',
                    secret TEXT NOT NULL COMMENT '主密钥AES加密的TOTP种子(Hex)',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码库TOTP种子表'
                """);
        } catch (Exception e) {
            log.warn("t_vault_totp 建表失败（不影响启动）: {}", e.getMessage());
        }
    }
}
