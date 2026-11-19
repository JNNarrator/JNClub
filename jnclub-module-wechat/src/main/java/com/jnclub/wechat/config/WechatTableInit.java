package com.jnclub.wechat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 微信通道模块的启动幂等建表（沿用 ClipboardTableInit 的模式：表不存在才 CREATE，零人工迁移）。
 *
 * <p>表分工：
 * <ul>
 *   <li>{@code t_wechat_credentials} 扫码登录后拿到的 bot token，服务重启后免重新扫码</li>
 *   <li>{@code t_wechat_context_token} 每个对话的 context_token —— <b>主动推送的唯一凭据</b></li>
 *   <li>{@code t_wechat_msg_seen} 已处理的消息幂等键，防 getUpdates 重放导致重复写入剪贴板</li>
 *   <li>{@code t_wechat_config} 通道开关等单例配置</li>
 * </ul>
 *
 * <p>为什么 context_token 一定要落库：原 clawbot 把它放在纯内存的 ConcurrentHashMap 里，
 * 进程一重启就全丢，而发送消息的第一行就是 getOrThrow()——于是"电脑复制 → 手机收到"
 * 会在每次部署后静默失效，必须等用户重新给 bot 发一条消息才能恢复。落库 + 启动预热
 * 是让这条链路在重启后依然可用的前提。
 */
@Slf4j
@Component
@Order(0)
public class WechatTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public WechatTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable("t_wechat_credentials", """
                CREATE TABLE IF NOT EXISTS t_wechat_credentials (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(128) NOT NULL COMMENT 'JNClub 用户标识，单用户自用时为固定值',
                    bot_token VARCHAR(512) NOT NULL COMMENT 'iLink bot token',
                    base_url VARCHAR(255) DEFAULT NULL COMMENT 'iLink 服务地址',
                    account_id VARCHAR(255) DEFAULT NULL COMMENT '微信账号标识',
                    wechat_user_id VARCHAR(255) DEFAULT NULL COMMENT '扫码登录的微信 userId',
                    saved_at BIGINT DEFAULT NULL COMMENT '保存时间戳',
                    updated_at BIGINT NOT NULL DEFAULT 0,
                    UNIQUE KEY uk_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信 iLink 登录凭据'
                """);

        ensureTable("t_wechat_context_token", """
                CREATE TABLE IF NOT EXISTS t_wechat_context_token (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(128) NOT NULL COMMENT 'JNClub 用户标识',
                    peer_id VARCHAR(255) NOT NULL COMMENT '对话方微信 userId（xxx@im.wechat）',
                    context_token VARCHAR(1024) NOT NULL COMMENT '发送消息时必须回带的 context_token',
                    updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '最后一次刷新时间',
                    UNIQUE KEY uk_user_peer (user_id, peer_id),
                    KEY idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每个对话的 context_token，主动推送凭据'
                """);

        ensureTable("t_wechat_msg_seen", """
                CREATE TABLE IF NOT EXISTS t_wechat_msg_seen (
                    client_id VARCHAR(128) NOT NULL PRIMARY KEY COMMENT 'iLink 消息 client_id，天然幂等键',
                    user_id VARCHAR(128) NOT NULL,
                    seen_at BIGINT NOT NULL DEFAULT 0,
                    KEY idx_seen (seen_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已处理消息幂等表'
                """);

        ensureTable("t_wechat_config", """
                CREATE TABLE IF NOT EXISTS t_wechat_config (
                    user_id VARCHAR(128) NOT NULL PRIMARY KEY,
                    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '通道总开关',
                    clipboard_bridge TINYINT NOT NULL DEFAULT 1 COMMENT '是否桥接剪贴板',
                    whitelist VARCHAR(1000) DEFAULT NULL COMMENT '允许写入剪贴板的微信 userId，逗号分隔',
                    notify_on_copy TINYINT NOT NULL DEFAULT 1 COMMENT '电脑复制后是否推送到微信',
                    updated_at BIGINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信通道配置'
                """);

        log.info("微信通道表初始化完成");
    }

    private void ensureTable(String name, String ddl) {
        try {
            jdbcTemplate.execute(ddl);
            log.info("表 {} 就绪", name);
        } catch (Exception e) {
            log.error("建表 {} 失败：{}", name, e.getMessage());
        }
    }
}
