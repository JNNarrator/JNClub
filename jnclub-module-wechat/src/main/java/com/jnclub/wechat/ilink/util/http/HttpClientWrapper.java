package com.jnclub.wechat.ilink.util.http;
import com.jnclub.wechat.ilink.exception.ApiException;
import lombok.SneakyThrows;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 客户端包装
 */
public class HttpClientWrapper implements AutoCloseable {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

	@SneakyThrows
    public HttpClientWrapper() {
		// 信任所有证书
		TrustStrategy trustStrategy = (chain, authType) -> true;
		
		SSLContext sslContext = SSLContextBuilder.create()
				.loadTrustMaterial(trustStrategy)
				.build();
		
		SSLConnectionSocketFactory sslSocketFactory =
				new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
		
		PoolingHttpClientConnectionManager connectionManager =
				PoolingHttpClientConnectionManagerBuilder.create()
						.setSSLSocketFactory(sslSocketFactory)
						.build();
		
		this.httpClient =  HttpClients.custom()
			    .setConnectionManager(connectionManager)
			    .build();
        // Jackson 3.x（注解仍来自 com.fasterxml.jackson.annotation，官方兼容安排）
        this.objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * 发送 GET 请求
     */
    public String get(String url, Map<String, String> headers, Duration timeout) throws ApiException {
        HttpGet httpGet = new HttpGet(url);
        return executeRequest(httpGet, headers, timeout);
    }

    /**
     * 发送 POST 请求
     */
    public String post(String url, Map<String, String> headers, Object body, Duration timeout) throws ApiException {
        HttpPost httpPost = new HttpPost(url);

        if (body != null) {
			try {
				String jsonBody = objectMapper.writeValueAsString(body);
				httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
			}catch (Exception e){
				e.printStackTrace();
			}
        
        }

        return executeRequest(httpPost, headers, timeout);
    }

    /**
     * 上传二进制到 CDN，返回响应头里的 {@code x-encrypted-param}。
     *
     * <p>CDN 不回 JSON，也不回正文——下载参数在响应头里，所以这里
     * 不能复用 executeRequest（它把正文当字符串返回）。
     */
    public String postBinary(String url, byte[] body, Duration timeout) throws ApiException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new org.apache.hc.core5.http.io.entity.ByteArrayEntity(
                body, ContentType.APPLICATION_OCTET_STREAM));
        RequestConfig config = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .build();
        httpPost.setConfig(config);
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                throw new ApiException("CDN 上传失败，HTTP " + status);
            }
            org.apache.hc.core5.http.Header header = response.getFirstHeader("x-encrypted-param");
            if (header == null || header.getValue() == null || header.getValue().isBlank()) {
                throw new ApiException("CDN 未返回 x-encrypted-param");
            }
            return header.getValue();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("CDN 上传异常: " + e.getMessage(), e);
        }
    }

    /** 从 CDN 下载密文（原始字节，不做任何解码）。 */
    public byte[] getBinary(String url, Duration timeout) throws ApiException {
        HttpGet httpGet = new HttpGet(url);
        RequestConfig config = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .build();
        httpGet.setConfig(config);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                throw new ApiException("CDN 下载失败，HTTP " + status);
            }
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new ApiException("CDN 返回空内容");
            }
            return EntityUtils.toByteArray(entity);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("CDN 下载异常: " + e.getMessage(), e);
        }
    }

    private String executeRequest(HttpUriRequestBase request, Map<String, String> headers, Duration timeout) throws ApiException {
        // 设置请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // 设置超时
        if (timeout != null) {
            RequestConfig config = RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                    .build();
            request.setConfig(config);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return "";
            }
            String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            EntityUtils.consume(entity);
            return result;
        } catch (Exception e) {
            throw new ApiException("Failed to parse response", e);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
