package com.jnclub.clipboard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 剪贴板同步模块的启动幂等建表（沿用 NotificationTableInit / FeatureTableInit 的模式：
 * 表不存在才 CREATE，列不存在才 ALTER，零人工迁移）。
 *
 * <p>表分工：
 * <ul>
 *   <li>{@code t_clipboard_record} 条目主体，{@code seq} 为服务端分配的同步游标</li>
 *   <li>{@code t_clipboard_setting} 设置同步（白名单键），与条目共用同一 seq 空间</li>
 *   <li>{@code t_clipboard_blob} 内容寻址附件元数据，实体文件存 dufs</li>
 *   <li>{@code t_clipboard_user_seq} 每用户单调递增游标，push 时原子递增</li>
 * </ul>
 *
 * <p>为什么 seq 由服务端分配：客户端时钟不可信（跨设备可能差几分钟甚至几小时），
 * 若用客户端时间做增量游标，会出现「本机时间慢的设备永远拉不到新数据」。
 * 服务端单调递增的 seq 天然全局有序，且给出稳定的「谁先谁后」全序。
 */
@Slf4j
@Component
public class ClipboardTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ClipboardTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable("t_clipboard_record", """
                CREATE TABLE IF NOT EXISTS t_clipboard_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    sync_id CHAR(36) NOT NULL COMMENT '客户端生成的稳定标识，跨设备同一逻辑条目',
                    content_type VARCHAR(32) NOT NULL DEFAULT 'text' COMMENT 'text/html/image/file_path',
                    content LONGTEXT COMMENT '正文；image 为 blob sha256 引用',
                    content_hash BIGINT NOT NULL DEFAULT 0 COMMENT '正文归一化哈希，跨设备判重用',
                    html_content LONGTEXT COMMENT '富文本原始 HTML',
                    preview VARCHAR(1000) DEFAULT '' COMMENT '列表摘要',
                    source_app VARCHAR(200) DEFAULT '' COMMENT '来源应用',
                    is_pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '置顶标记',
                    pinned_order INT DEFAULT NULL COMMENT '置顶顺序',
                    tags VARCHAR(1000) DEFAULT '' COMMENT 'JSON 数组，如 ["工作","代码"]',
                    note VARCHAR(2000) DEFAULT NULL COMMENT '用户备注',
                    use_count INT NOT NULL DEFAULT 0 COMMENT '使用次数',
                    created_at BIGINT NOT NULL DEFAULT 0 COMMENT '首次收录时间（客户端毫秒）',
                    last_used_at BIGINT NOT NULL DEFAULT 0 COMMENT '最近使用时间（客户端毫秒）',
                    sort_at BIGINT NOT NULL DEFAULT 0 COMMENT '列表排序键（客户端毫秒）',
                    deleted_at BIGINT DEFAULT NULL COMMENT '墓碑时间；非空表示已删除，仍需下发对端',
                    rev BIGINT NOT NULL DEFAULT 1 COMMENT '客户端版本号，push 时做乐观锁',
                    seq BIGINT NOT NULL DEFAULT 0 COMMENT '服务端同步游标，pull 按此增量',
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_user_sync (user_id, sync_id),
                    KEY idx_user_seq (user_id, seq),
                    KEY idx_user_sort (user_id, deleted_at, sort_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剪贴板条目'
                """);

        ensureTable("t_clipboard_setting", """
                CREATE TABLE IF NOT EXISTS t_clipboard_setting (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    setting_key VARCHAR(120) NOT NULL COMMENT '设置键，仅白名单键允许同步',
                    setting_value TEXT COMMENT '值，统一按字符串存',
                    deleted_at BIGINT DEFAULT NULL COMMENT '墓碑时间',
                    rev BIGINT NOT NULL DEFAULT 1 COMMENT '客户端版本号',
                    seq BIGINT NOT NULL DEFAULT 0 COMMENT '服务端同步游标',
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_user_key (user_id, setting_key),
                    KEY idx_user_seq (user_id, seq)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剪贴板设置同步'
                """);

        ensureTable("t_clipboard_blob", """
                CREATE TABLE IF NOT EXISTS t_clipboard_blob (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识，按用户隔离附件',
                    sha256 CHAR(64) NOT NULL COMMENT '内容哈希，内容寻址',
                    byte_size BIGINT NOT NULL DEFAULT 0 COMMENT '字节数',
                    mime VARCHAR(120) DEFAULT '' COMMENT 'MIME 类型',
                    ref_count INT NOT NULL DEFAULT 0 COMMENT '引用计数，为 0 且超期后由 GC 清理',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_user_sha (user_id, sha256),
                    KEY idx_ref (user_id, ref_count, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剪贴板附件元数据（实体在 dufs）'
                """);

        ensureTable("t_clipboard_user_seq", """
                CREATE TABLE IF NOT EXISTS t_clipboard_user_seq (
                    user_id VARCHAR(64) PRIMARY KEY COMMENT 'SSO用户标识',
                    seq BIGINT NOT NULL DEFAULT 0 COMMENT '已分配的最大游标',
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剪贴板每用户同步游标'
                """);
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
