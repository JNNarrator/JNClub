package com.jnclub.wechat.ilink.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * getUploadUrl 请求：向服务端申请一个 CDN 上传位。
 *
 * <p>先把尺寸和摘要报上去，服务端才能预先分配并校验——所以
 * {@code rawsize}/{@code rawfilemd5} 必须是<b>加密前</b>的值，
 * {@code filesize} 是<b>加密后</b>的值。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetUploadUrlRequest {

    /** 随机 16 字节 hex */
    @JsonProperty("filekey")
    private String filekey;

    /** 1=IMAGE, 2=VIDEO, 3=FILE, 4=VOICE */
    @JsonProperty("media_type")
    private Integer mediaType;

    @JsonProperty("to_user_id")
    private String toUserId;

    /** 明文字节数 */
    @JsonProperty("rawsize")
    private Long rawsize;

    /** 明文 MD5 hex */
    @JsonProperty("rawfilemd5")
    private String rawfilemd5;

    /** AES 加密后的密文字节数 */
    @JsonProperty("filesize")
    private Long filesize;

    @JsonProperty("no_need_thumb")
    private Boolean noNeedThumb;

    /** AES 密钥 hex */
    @JsonProperty("aeskey")
    private String aeskey;

    @JsonProperty("base_info")
    private BaseInfo baseInfo;

    public String getFilekey() {
        return filekey;
    }

    public void setFilekey(String filekey) {
        this.filekey = filekey;
    }

    public Integer getMediaType() {
        return mediaType;
    }

    public void setMediaType(Integer mediaType) {
        this.mediaType = mediaType;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public Long getRawsize() {
        return rawsize;
    }

    public void setRawsize(Long rawsize) {
        this.rawsize = rawsize;
    }

    public String getRawfilemd5() {
        return rawfilemd5;
    }

    public void setRawfilemd5(String rawfilemd5) {
        this.rawfilemd5 = rawfilemd5;
    }

    public Long getFilesize() {
        return filesize;
    }

    public void setFilesize(Long filesize) {
        this.filesize = filesize;
    }

    public Boolean getNoNeedThumb() {
        return noNeedThumb;
    }

    public void setNoNeedThumb(Boolean noNeedThumb) {
        this.noNeedThumb = noNeedThumb;
    }

    public String getAeskey() {
        return aeskey;
    }

    public void setAeskey(String aeskey) {
        this.aeskey = aeskey;
    }

    public BaseInfo getBaseInfo() {
        return baseInfo;
    }

    public void setBaseInfo(BaseInfo baseInfo) {
        this.baseInfo = baseInfo;
    }
}
