package com.jnclub.wechat.ilink.api;



import com.jnclub.wechat.ilink.exception.ApiException;
import com.jnclub.wechat.ilink.model.auth.Credentials;
import com.jnclub.wechat.ilink.model.request.GetUpdatesRequest;
import com.jnclub.wechat.ilink.model.request.GetUploadUrlRequest;
import com.jnclub.wechat.ilink.model.response.GetUploadUrlResponse;
import com.jnclub.wechat.ilink.model.request.SendMessageRequest;
import com.jnclub.wechat.ilink.model.response.ApiResponse;
import com.jnclub.wechat.ilink.model.response.GetUpdatesResponse;
import com.jnclub.wechat.ilink.model.response.QrcodeResponse;
import com.jnclub.wechat.ilink.model.response.QrcodeStatusResponse;
import com.jnclub.wechat.ilink.util.ClientIdGenerator;
import com.jnclub.wechat.ilink.util.UinGenerator;
import com.jnclub.wechat.ilink.util.http.HttpClientWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ilink API 客户端
 */
public class IlinkApiClient implements AutoCloseable {

    private final HttpClientWrapper httpClient;
    private final String baseUrl;
    private Credentials credentials;

    public IlinkApiClient(String baseUrl) {
        this.httpClient = new HttpClientWrapper();
        this.baseUrl = baseUrl;
    }

    /**
     * 设置凭据
     */
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * 构建请求头
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("AuthorizationType", "ilink_bot_token");
        headers.put("X-WECHAT-UIN", UinGenerator.generate());
        headers.put("Content-Type", "application/json");
        if (credentials != null && credentials.getToken() != null) {
            headers.put("Authorization", "Bearer " + credentials.getToken());
        }

        return headers;
    }

    /**
     * 获取二维码
     *
     * @param botType bot类型 (3为ClawBot)
     */
    public QrcodeResponse getBotQrcode(String botType, Duration timeout) throws ApiException {
        String url = baseUrl + "/ilink/bot/get_bot_qrcode?bot_type=" + botType;
        Map<String, String> headers = buildHeaders();

        String response = httpClient.get(url, headers, timeout);
        return parseResponse(response, QrcodeResponse.class);
    }

    /**
     * 获取二维码状态
     */
    public QrcodeStatusResponse getQrcodeStatus(String qrcode, Duration timeout) throws ApiException {
        String url = baseUrl + "/ilink/bot/get_qrcode_status?qrcode=" + qrcode;
        Map<String, String> headers = buildHeaders();
        headers.put("iLink-App-ClientVersion", "1");

        String response = httpClient.get(url, headers, timeout);
        return parseResponse(response, QrcodeStatusResponse.class);
    }

    /**
     * 获取消息更新（长轮询）
     */
    public GetUpdatesResponse getUpdates(GetUpdatesRequest request, Duration timeout) throws ApiException {
        String url = baseUrl + "/ilink/bot/getupdates";
        Map<String, String> headers = buildHeaders();

        String response = httpClient.post(url, headers, request, timeout);
        return parseResponse(response, GetUpdatesResponse.class);
    }

    /**
     * 发送消息
     */
    public ApiResponse sendMessage(SendMessageRequest request, Duration timeout) throws ApiException {
        // 设置 client_id
        if (request.getMsg() != null && request.getMsg().getClientId() == null) {
            request.getMsg().setClientId(ClientIdGenerator.generate());
        }

        String url = baseUrl + "/ilink/bot/sendmessage";
        Map<String, String> headers = buildHeaders();

        String response = httpClient.post(url, headers, request, timeout);
        return parseResponse(response, ApiResponse.class);
    }

    /**
     * 申请一个 CDN 上传位。
     */
    public GetUploadUrlResponse getUploadUrl(GetUploadUrlRequest request, Duration timeout) throws ApiException {
        String url = baseUrl + "/ilink/bot/getuploadurl";
        Map<String, String> headers = buildHeaders();
        String response = httpClient.post(url, headers, request, timeout);
        return parseResponse(response, GetUploadUrlResponse.class);
    }

    /** 上传密文到 CDN，返回下载参数（响应头 x-encrypted-param）。 */
    public String uploadToCdn(String cdnBaseUrl, String uploadParam, String filekey, byte[] cipherText, Duration timeout)
            throws ApiException {
        String url = cdnBaseUrl + "/upload?encrypted_query_param=" + uploadParam + "&filekey=" + filekey;
        return httpClient.postBinary(url, cipherText, timeout);
    }

    /** 从 CDN 下载密文。 */
    public byte[] downloadFromCdn(String cdnBaseUrl, String encryptQueryParam, Duration timeout) throws ApiException {
        String url = cdnBaseUrl + "/download?encrypted_query_param=" + encryptQueryParam;
        return httpClient.getBinary(url, timeout);
    }

    private <T> T parseResponse(String json, Class<T> clazz) throws ApiException {
        try {
            return httpClient.getObjectMapper().readValue(json, clazz);
        } catch (Exception e) {
            throw new ApiException("Failed to parse response: " + json.substring(0, Math.min(100, json.length())), e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
