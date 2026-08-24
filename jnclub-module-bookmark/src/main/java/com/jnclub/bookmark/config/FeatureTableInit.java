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
 * 2. t_bookmark 增加 read_later（稍后读标记）/ read_progress（阅读进度）/ read_at（最近阅读时间）
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

        // 稍后读 + 阅读进度（①）
        ensureColumn("t_bookmark", "read_later",
                "ALTER TABLE t_bookmark ADD COLUMN read_later TINYINT DEFAULT 0 NOT NULL COMMENT '稍后读标记：0否 1是' AFTER checked_at");
        ensureColumn("t_bookmark", "read_progress",
                "ALTER TABLE t_bookmark ADD COLUMN read_progress INT DEFAULT 0 NOT NULL COMMENT '阅读进度 0-100' AFTER read_later");
        ensureColumn("t_bookmark", "read_at",
                "ALTER TABLE t_bookmark ADD COLUMN read_at DATETIME DEFAULT NULL COMMENT '最近阅读时间' AFTER read_progress");

        // 网页快照归档（②）
        ensureTable("t_bookmark_snapshot", """
                CREATE TABLE IF NOT EXISTS t_bookmark_snapshot (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    bookmark_id BIGINT NOT NULL COMMENT '收藏ID',
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    dufs_key VARCHAR(512) NOT NULL COMMENT 'dufs 存储路径(相对 base-url)',
                    title VARCHAR(500) DEFAULT '' COMMENT '快照时页面标题',
                    url VARCHAR(2048) DEFAULT '' COMMENT '快照时 URL',
                    size INT DEFAULT 0 COMMENT '快照字节数',
                    captured_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
                    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1已删除',
                    INDEX idx_bookmark (bookmark_id),
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏网页快照表'
                """);

        // RSS 阅读器（④）
        ensureTable("t_feed", """
                CREATE TABLE IF NOT EXISTS t_feed (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    url VARCHAR(1024) NOT NULL COMMENT '订阅源地址',
                    title VARCHAR(300) DEFAULT '' COMMENT '源标题',
                    site_url VARCHAR(1024) DEFAULT '' COMMENT '站点地址',
                    icon VARCHAR(1024) DEFAULT '' COMMENT '站点图标',
                    last_fetched_at DATETIME DEFAULT NULL COMMENT '最近抓取时间',
                    fetch_interval_min INT DEFAULT 30 NOT NULL COMMENT '抓取间隔(分钟)',
                    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1已删除',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RSS 订阅源表'
                """);
        ensureTable("t_feed_item", """
                CREATE TABLE IF NOT EXISTS t_feed_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    feed_id BIGINT NOT NULL COMMENT '订阅源ID',
                    user_id VARCHAR(64) NOT NULL COMMENT 'SSO用户标识',
                    guid VARCHAR(512) NOT NULL COMMENT '条目唯一标识(源内)',
                    title VARCHAR(500) DEFAULT '' COMMENT '标题',
                    link VARCHAR(2048) DEFAULT '' COMMENT '原文链接',
                    author VARCHAR(200) DEFAULT '' COMMENT '作者',
                    summary TEXT COMMENT '摘要',
                    content MEDIUMTEXT COMMENT '全文(HTML)',
                    published_at DATETIME DEFAULT NULL COMMENT '发布时间',
                    fetched_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
                    read_flag TINYINT DEFAULT 0 NOT NULL COMMENT '已读：0未读 1已读',
                    starred TINYINT DEFAULT 0 NOT NULL COMMENT '星标：0否 1是',
                    deleted TINYINT DEFAULT 0 NOT NULL COMMENT '软删除标记：0正常 1已删除',
                    UNIQUE KEY uk_feed_guid (feed_id, guid),
                    INDEX idx_user_read (user_id, read_flag),
                    INDEX idx_feed (feed_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RSS 条目表'
                """);
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
