package com.jnclub.wechat.ilink.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件消息项（{@code MessageItem.type = 4}）。
 *
 * <p>注意 {@code len} 是<b>字符串</b>而不是数字——协议就是这么定义的。
 * 写成 long 会在反序列化时类型不匹配，而这类错误往往表现为"字段丢失"
 * 而不是明确报错，所以这里按协议原样保留为 String。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileItem {

    @JsonProperty("media")
    private CDNMedia media;

    /** 显示给用户的文件名，含扩展名。 */
    @JsonProperty("file_name")
    private String fileName;

    /** 明文 MD5（hex），服务端用它校验。 */
    @JsonProperty("md5")
    private String md5;

    /** 明文字节数，字符串形式。 */
    @JsonProperty("len")
    private String len;

    public CDNMedia getMedia() {
        return media;
    }

    public void setMedia(CDNMedia media) {
        this.media = media;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getLen() {
        return len;
    }

    public void setLen(String len) {
        this.len = len;
    }
}
