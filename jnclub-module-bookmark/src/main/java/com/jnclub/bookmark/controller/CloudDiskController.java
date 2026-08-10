package com.jnclub.bookmark.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.service.CloudDiskService;
import com.jnclub.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 云盘控制器 — 分片上传 / 断点续传 / 文件列表 / 删除 / 下载
 * 走 Sa-Token 登录鉴权（WebMvcConfig 默认拦截，无需放行）
 */
@Slf4j
@RestController
@RequestMapping("/api/clouddisk")
@RequiredArgsConstructor
public class CloudDiskController {

    private final CloudDiskService cloudDiskService;

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.username:}")
    private String dufsUser;

    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    // ============================================================
    // 分片上传
    // ============================================================

    /** 初始化上传（单文件），返回 uploadId 与分片配置 */
    @PostMapping("/upload/init")
    public R<Map<String, Object>> initUpload(@RequestBody Map<String, Object> body) {
        String filename = (String) body.get("filename");
        long totalSize = Long.parseLong(String.valueOf(body.getOrDefault("size", 0)));
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        Integer chunkSizeMb = body.get("chunkSizeMb") == null
                ? null : Integer.parseInt(String.valueOf(body.get("chunkSizeMb")));
        return R.ok(cloudDiskService.initUpload(filename, totalSize, directoryId, chunkSizeMb));
    }

    /** 上传单个分片（multipart：uploadId, chunkIndex, file） */
    @PostMapping("/upload/chunk")
    public R<Void> uploadChunk(@RequestParam("uploadId") String uploadId,
                               @RequestParam("chunkIndex") int chunkIndex,
                               @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            cloudDiskService.saveChunk(uploadId, chunkIndex, in);
        }
        return R.ok();
    }

    /** 查询分片进度（断点续传跳过已传分片） */
    @GetMapping("/upload/status")
    public R<Map<String, Object>> uploadStatus(@RequestParam("uploadId") String uploadId) throws IOException {
        return R.ok(cloudDiskService.getUploadStatus(uploadId));
    }

    /** 合并分片并入库，返回文件记录 */
    @PostMapping("/upload/complete")
    public R<FileRecord> complete(@RequestBody Map<String, String> body) throws IOException {
        String uploadId = body.get("uploadId");
        return R.ok(cloudDiskService.complete(uploadId));
    }

    // ============================================================
    // 文件管理
    // ============================================================

    /** 按云盘目录列出文件（该用户） */
    @GetMapping("/files")
    public R<List<FileRecord>> listFiles(@RequestParam("directoryId") Long directoryId) {
        return R.ok(cloudDiskService.listFiles(directoryId));
    }

    /** 云盘文件排序（拖拽排序，同一目录内） */
    @PutMapping("/files/sort")
    public R<Void> updateSortOrder(@RequestBody List<Map<String, Object>> sortList) {
        cloudDiskService.updateSortOrder(sortList);
        return R.ok();
    }

    /** 删除文件（软删除，进入回收站） */
    @DeleteMapping("/files/{id}")
    public R<Void> deleteFile(@PathVariable Long id) {
        cloudDiskService.deleteFile(id);
        return R.ok();
    }

    /** 批量删除文件（软删除，进入回收站） */
    @DeleteMapping("/files/batch")
    public R<Void> deleteFilesBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("ids");
        List<Long> ids = rawIds.stream().map(v -> Long.parseLong(String.valueOf(v))).toList();
        cloudDiskService.deleteFilesBatch(ids);
        return R.ok();
    }

    /** 重命名文件（仅改显示名，不动 dufs 物理路径） */
    @PutMapping("/files/{id}/rename")
    public R<Void> renameFile(@PathVariable Long id, @RequestBody Map<String, String> body) {
        cloudDiskService.renameFile(id, body.get("name"));
        return R.ok();
    }

    /** 移动文件到其他云盘目录 */
    @PutMapping("/files/{id}/move")
    public R<Void> moveFile(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        cloudDiskService.moveFile(id, directoryId);
        return R.ok();
    }

    /** 批量移动文件到其他云盘目录 */
    @PutMapping("/files/move-batch")
    public R<Void> moveFilesBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("ids");
        List<Long> ids = rawIds.stream().map(v -> Long.parseLong(String.valueOf(v))).toList();
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        cloudDiskService.moveFilesBatch(ids, directoryId);
        return R.ok();
    }

    /**
     * 下载文件：流式代理 dufs，并按 t_file 中的原始文件名设置 Content-Disposition
     */
    @GetMapping("/files/{id}/download")
    public void download(@PathVariable Long id,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        FileRecord record = cloudDiskService.getFile(id);

        String dufsUrl = dufsBaseUrl + "/" + record.getStoredKey();
        var req = HttpRequest.get(dufsUrl);
        if (dufsUser != null && !dufsUser.isBlank()) {
            String auth = dufsUser + ":" + dufsPass;
            req.header("Authorization", "Basic " +
                    Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
        }

        HttpResponse dufsResp = req.execute();
        if (dufsResp.getStatus() != 200) {
            log.warn("云盘下载 dufs 失败: status={} url={}", dufsResp.getStatus(), dufsUrl);
            response.sendError(404);
            return;
        }

        String filename = record.getOriginalName() == null ? "download" : record.getOriginalName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(record.getMime() == null ? "application/octet-stream" : record.getMime());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(dufsResp.bodyBytes().length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(dufsResp.bodyBytes());
        }
    }

    /** 健康检查用：清理孤儿临时目录（可选，供运维手动触发） */
    @DeleteMapping("/temp-clean")
    public R<Integer> cleanTemp(@RequestParam(value = "days", defaultValue = "1") int days) {
        return R.ok(cloudDiskService.cleanTempDirs(days));
    }
}
