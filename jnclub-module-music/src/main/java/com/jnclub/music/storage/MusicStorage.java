package com.jnclub.music.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 音乐存储抽象接口
 */
public interface MusicStorage {

    /**
     * 列出指定文件夹下的文件和子文件夹
     */
    StorageListResult listFiles(String folderId, int page);

    /**
     * 获取文件的下载直链
     */
    String getDownloadUrl(String fileId);

    /**
     * 获取文件的下载直链及其真实过期时间。
     * <p>过期时间来自存储方的直链签名（如 <code>e=</code> 参数），
     * 用于让缓存有效期与直链真实有效期对齐，避免返回已失效的直链。</p>
     */
    DownloadUrl getDownloadUrlWithExpiry(String fileId);

    /**
     * 批量获取文件的下载直链
     * @param fileIds 文件ID列表
     * @return fileId -> downloadUrl 的映射
     */
    Map<String, String> getDownloadUrls(List<String> fileIds);

    /**
     * 批量获取文件的下载直链及其真实过期时间。
     * @param fileIds 文件ID列表
     * @return fileId -> 直链及过期时间 的映射
     */
    Map<String, DownloadUrl> getDownloadUrlsWithExpiry(List<String> fileIds);

    /**
     * 获取存储实现名称
     */
    String getStorageName();
}
