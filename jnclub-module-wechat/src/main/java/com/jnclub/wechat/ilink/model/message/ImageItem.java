package com.jnclub.wechat.ilink.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 图片消息项。
 *
 * <p>协议里 {@code MessageItem.type = 2} 就是图片——原 SDK 建模时跳过了这个值
 * （只定义了 TEXT=1、VOICE=3），不是协议不支持。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageItem {

    @JsonProperty("media")
    private CDNMedia media;

    @JsonProperty("thumb_media")
    private CDNMedia thumbMedia;

    /** hex 字符串形式的 AES key，优先于 media.aes_key */
    @JsonProperty("aeskey")
    private String aeskey;

    /** 中图密文大小 */
    @JsonProperty("mid_size")
    private Integer midSize;

    /** 高清密文大小 */
    @JsonProperty("hd_size")
    private Integer hdSize;

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

    public CDNMedia getThumbMedia() {
        return thumbMedia;
    }

    public void setThumbMedia(CDNMedia thumbMedia) {
        this.thumbMedia = thumbMedia;
    }

    public String getAeskey() {
        return aeskey;
    }

    public void setAeskey(String aeskey) {
        this.aeskey = aeskey;
    }

    public Integer getMidSize() {
        return midSize;
    }

    public void setMidSize(Integer midSize) {
        this.midSize = midSize;
    }

    public Integer getHdSize() {
        return hdSize;
    }

    public void setHdSize(Integer hdSize) {
        this.hdSize = hdSize;
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
