package com.jnclub.wechat.ilink.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 视频消息项（{@code MessageItem.type = 5}）。
 *
 * <p>缩略图字段先留但不填：生成视频封面需要解码视频，成本远高于收益，
 * 而协议并不强制要求（上传时 {@code no_need_thumb=true}）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoItem {

    @JsonProperty("media")
    private CDNMedia media;

    /** 明文（加密前）字节数。 */
    @JsonProperty("video_size")
    private Long videoSize;

    /** 时长（毫秒）；不填不影响收发。 */
    @JsonProperty("play_length")
    private Long playLength;

    /** 明文 MD5（hex）。 */
    @JsonProperty("video_md5")
    private String videoMd5;

    @JsonProperty("thumb_media")
    private CDNMedia thumbMedia;

    @JsonProperty("thumb_size")
    private Integer thumbSize;

    @JsonProperty("thumb_height")
    private Integer thumbHeight;

    @JsonProperty("thumb_width")
    private Integer thumbWidth;

    public CDNMedia getMedia() {
        return media;
    }

    public void setMedia(CDNMedia media) {
        this.media = media;
    }

    public Long getVideoSize() {
        return videoSize;
    }

    public void setVideoSize(Long videoSize) {
        this.videoSize = videoSize;
    }

    public Long getPlayLength() {
        return playLength;
    }

    public void setPlayLength(Long playLength) {
        this.playLength = playLength;
    }

    public String getVideoMd5() {
        return videoMd5;
    }

    public void setVideoMd5(String videoMd5) {
        this.videoMd5 = videoMd5;
    }

    public CDNMedia getThumbMedia() {
        return thumbMedia;
    }

    public void setThumbMedia(CDNMedia thumbMedia) {
        this.thumbMedia = thumbMedia;
    }

    public Integer getThumbSize() {
        return thumbSize;
    }

    public void setThumbSize(Integer thumbSize) {
        this.thumbSize = thumbSize;
    }

    public Integer getThumbHeight() {
        return thumbHeight;
    }

    public void setThumbHeight(Integer thumbHeight) {
        this.thumbHeight = thumbHeight;
    }

    public Integer getThumbWidth() {
        return thumbWidth;
    }

    public void setThumbWidth(Integer thumbWidth) {
        this.thumbWidth = thumbWidth;
    }
}
