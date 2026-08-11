package com.jnclub.music.storage;

import java.time.Instant;

/**
 * 下载直链及其真实过期时间。
 *
 * @param url       直链地址
 * @param expiresAt 直链真实过期时间（来自存储方签名，如蓝奏云 <code>e=</code> 参数）
 */
public record DownloadUrl(String url, Instant expiresAt) {

    public static DownloadUrl of(String url, Instant expiresAt) {
        return new DownloadUrl(url, expiresAt != null ? expiresAt : Instant.now().plusSeconds(3600));
    }
}