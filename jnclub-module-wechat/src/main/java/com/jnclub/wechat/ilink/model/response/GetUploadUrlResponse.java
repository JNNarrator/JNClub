package com.jnclub.wechat.ilink.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * getUploadUrl 响应：拿到上传位参数。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetUploadUrlResponse extends ApiResponse {

    /** 上传加密参数，拼进 CDN 上传 URL */
    @JsonProperty("upload_param")
    private String uploadParam;

    /** 缩略图上传参数（no_need_thumb=true 时不返回） */
    @JsonProperty("thumb_upload_param")
    private String thumbUploadParam;

    public String getUploadParam() {
        return uploadParam;
    }

    public void setUploadParam(String uploadParam) {
        this.uploadParam = uploadParam;
    }

    public String getThumbUploadParam() {
        return thumbUploadParam;
    }

    public void setThumbUploadParam(String thumbUploadParam) {
        this.thumbUploadParam = thumbUploadParam;
    }
}
