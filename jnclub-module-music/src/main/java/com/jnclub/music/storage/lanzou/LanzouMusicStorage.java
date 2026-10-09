package com.jnclub.music.storage.lanzou;

import com.jnclub.music.lanzou.LanzouApiClient;
import com.jnclub.music.lanzou.dto.LanzouDirectLink;
import com.jnclub.music.lanzou.dto.LanzouFile;
import com.jnclub.music.lanzou.dto.LanzouFolder;
import com.jnclub.music.lanzou.dto.LanzouPageResult;
import com.jnclub.music.storage.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * 蓝奏云存储实现
 */
@Component
public class LanzouMusicStorage implements MusicStorage {


    /** 批量取链并发度：兼顾吞吐与蓝奏云反爬容忍度。 */
    private static final int BATCH_CONCURRENCY = 10;
    /** 单次批量取链的总等待上限：个别文件卡住时不能拖死整个请求。 */
    private static final long BATCH_TIMEOUT_SECONDS = 30L;
    private final LanzouApiClient lanzouClient;
    /**
     * 批量取链线程池：使用守护线程并显式关闭，避免非守护线程阻止 JVM 退出。
     * <p>原先用 {@code Executors.newFixedThreadPool(10)} 创建的线程是非守护线程，
     * 且没有任何关闭钩子：应用停机时这些线程仍存活，会导致进程无法正常结束。</p>
     */
    private final ExecutorService executorService;

    public LanzouMusicStorage(LanzouApiClient lanzouClient) {
        this.lanzouClient = lanzouClient;
        this.executorService = Executors.newFixedThreadPool(BATCH_CONCURRENCY, r -> {
            Thread t = new Thread(r, "lanzou-url-batch");
            t.setDaemon(true);
            return t;
        });
    }

    @jakarta.annotation.PreDestroy
    void shutdownExecutor() {
        executorService.shutdownNow();
    }

    @Override
    public StorageListResult listFiles(String folderId, int page) {
        LanzouPageResult result = lanzouClient.listFiles(folderId, page);
        if (result == null) {
            return new StorageListResult(page, List.of(), List.of());
        }

        List<StorageFile> files = new ArrayList<>();
        if (result.files() != null) {
            for (LanzouFile f : result.files()) {
                files.add(new StorageFile(f.id(), f.name(), f.size()));
            }
        }

        List<StorageFolder> folders = new ArrayList<>();
        if (result.folders() != null) {
            for (LanzouFolder f : result.folders()) {
                folders.add(new StorageFolder(f.id(), f.name(), f.description()));
            }
        }

        return new StorageListResult(page, files, folders);
    }

    @Override
    public String getDownloadUrl(String fileId) {
        return getDownloadUrlWithExpiry(fileId).url();
    }

    @Override
    public DownloadUrl getDownloadUrlWithExpiry(String fileId) {
        try {
            LanzouDirectLink link = lanzouClient.getFileDownloadLink(fileId);
            Instant expiry = link.expiresAt() != null ? link.expiresAt() : Instant.now().plusSeconds(3600);
            return DownloadUrl.of(link.url(), expiry);
        } catch (Exception e) {
            throw new RuntimeException("获取下载链接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getDownloadUrls(List<String> fileIds) {
        Map<String, DownloadUrl> withExpiry = getDownloadUrlsWithExpiry(fileIds);
        Map<String, String> urlOnly = new HashMap<>();
        withExpiry.forEach((id, dl) -> urlOnly.put(id, dl.url()));
        return urlOnly;
    }

    @Override
    public Map<String, DownloadUrl> getDownloadUrlsWithExpiry(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }

        Map<String, DownloadUrl> result = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String fileId : fileIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    DownloadUrl dl = getDownloadUrlWithExpiry(fileId);
                    result.put(fileId, dl);
                } catch (Exception e) {
                    // 单个失败不影响其他
                }
            }, executorService);
            futures.add(future);
        }

        // 加总超时：原先 join() 无上限，任一取链卡住（网络挂起/反爬重试 sleep）都会
        // 让调用线程无限等待，进而拖死上层的列表/批量接口。
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(BATCH_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            // 超时返回已完成的部分结果，由上层按「未命中」处理并各自回源兜底
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            // 单个任务异常已在任务内部吞掉，这里仅防御性兜底
        }
        return result;
    }

    @Override
    public String getStorageName() {
        return "lanzou";
    }
}
