package com.jnclub.music.track.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可播放媒体地址，mediaUrl 需要由文件服务支持 HTTP Range。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUrlDTO {

    private String trackId;
    private String mediaUrl;
    private String format;
    private OffsetDateTime expiresAt;

    /**
     * 直链是否可播放。true=可直接播放；false=最近预检/取链失败（此时 mediaUrl 可能为 null）。
     */
    private Boolean playable;

    /**
     * 不可播放时的原因码/说明（如 LANZOU_SESSION_EXPIRED、MEDIA_UNAVAILABLE），可播时为 null。
     */
    private String message;

    /**
     * 备选播放地址（预留 dufs 等镜像源），当前无镜像时为空列表。
     */
    private List<String> fallbackUrls;
}
