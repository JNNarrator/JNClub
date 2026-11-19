package com.jnclub.wechat.ilink.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CDN 媒体引用，所有媒体类型（图片/语音/文件/视频）共用。
 *
 * <p>它不是文件本身，而是"去哪儿取、用什么密钥解"的两件信息：
 * {@code encrypt_query_param} 是下载参数（上传成功后由响应头
 * {@code x-encrypted-param} 给出），{@code aes_key} 是解密密钥。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CDNMedia {

    @JsonProperty("encrypt_query_param")
    private String encryptQueryParam;

    /** base64 编码的 AES-128 密钥；解码后可能是 16 字节，也可能是 32 字节的 hex 字符串。 */
    @JsonProperty("aes_key")
    private String aesKey;

    /** 0 = 只加密 fileid，1 = 打包缩略图等 */
    @JsonProperty("encrypt_type")
    private Integer encryptType;

    public CDNMedia() {
    }

    public CDNMedia(String encryptQueryParam, String aesKey) {
        this.encryptQueryParam = encryptQueryParam;
        this.aesKey = aesKey;
    }

    public String getEncryptQueryParam() {
        return encryptQueryParam;
    }

    public void setEncryptQueryParam(String encryptQueryParam) {
        this.encryptQueryParam = encryptQueryParam;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    public Integer getEncryptType() {
        return encryptType;
    }

    public void setEncryptType(Integer encryptType) {
        this.encryptType = encryptType;
    }
}
