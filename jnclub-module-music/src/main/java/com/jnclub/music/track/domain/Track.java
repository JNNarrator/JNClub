package com.jnclub.music.track.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌曲元数据，字段对齐数据库 `track` 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("music_track")
public class Track {

    /**
     * 全局唯一歌曲 ID，对应数据库 `track_id` 主键。
     */
    @TableId(value = "track_id", type = IdType.INPUT)
    private String trackId;

    private String name;

    private String artist;

    private String album;

    private String coverUrl;

    private Integer duration;

    private String format;

    private Long fileSize;

    private Integer trackNumber;

    private Boolean hasLyric;

    private String lyricUrl;

    private String mediaUrl;

    private java.time.OffsetDateTime urlExpiresAt;

    /**
     * 直链健康预检结果：1=可播放，0=最近一次预检失败（仍保留 mediaUrl 兜底）。
     */
    private Integer playable;

    /**
     * 最近一次直链预检/取链失败原因（如 LANZOU_SESSION_EXPIRED / MEDIA_UNAVAILABLE）。
     */
    private String lastError;
}
