package com.jnclub.music.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等创建音乐歌单相关表，并为播放历史补齐进度列。
 * - music_playlist：歌单
 * - music_playlist_item：歌单曲目
 * - music_play_history：补 progress_seconds / updated_at（跨设备继续播放）
 */
@Slf4j
@Component
public class MusicPlaylistTableInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public MusicPlaylistTableInit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS music_playlist (
                    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '歌单ID',
                    device_id   VARCHAR(128)  NOT NULL COMMENT '匿名设备ID',
                    name        VARCHAR(128)  NOT NULL COMMENT '歌单名称',
                    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    INDEX idx_playlist_device (device_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单表'
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS music_playlist_item (
                    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '歌单曲目ID',
                    playlist_id BIGINT        NOT NULL COMMENT '歌单ID',
                    track_id    VARCHAR(32)   NOT NULL COMMENT '歌曲ID',
                    position    INT           NOT NULL COMMENT '排序位置，从0开始',
                    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
                    UNIQUE KEY uk_playlist_item_track (playlist_id, track_id),
                    INDEX idx_playlist_item_playlist (playlist_id, position),
                    INDEX idx_playlist_item_track (track_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单曲目表'
                """);
        } catch (Exception e) {
            log.warn("music_playlist 建表失败（不影响启动）: {}", e.getMessage());
        }
        ensureColumn("music_play_history", "progress_seconds",
                "ALTER TABLE music_play_history ADD COLUMN progress_seconds INT DEFAULT 0 COMMENT '最近播放进度（秒）' AFTER played_at");
        ensureColumn("music_play_history", "updated_at",
                "ALTER TABLE music_play_history ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近上报时间' AFTER progress_seconds");
        // 播放直链健康预检：记录最近一次预检是否可播放及失败原因，供 getMediaUrl 快速判断
        ensureColumn("music_track", "playable",
                "ALTER TABLE music_track ADD COLUMN playable TINYINT(1) DEFAULT 1 COMMENT '直链健康预检：1可播放 0不可播' AFTER url_expires_at");
        ensureColumn("music_track", "last_error",
                "ALTER TABLE music_track ADD COLUMN last_error VARCHAR(128) DEFAULT NULL COMMENT '最近直链预检失败原因' AFTER playable");
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(alterSql);
        } catch (Exception e) {
            log.warn("{} 补列 {} 失败（不影响启动）: {}", table, column, e.getMessage());
        }
    }
}