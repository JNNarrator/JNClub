package com.jnclub.bookmark.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jnclub.bookmark.entity.NoteAsset;
import com.jnclub.bookmark.mapper.NoteAssetMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务 — 校验 MIME/大小 + Basic Auth 上传 dufs + 审计入库
 */
@Slf4j
@Service
public class UploadService {

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.public-url}")
    private String dufsPublicUrl;

    @Value("${jnclub.dufs.upload-path}")
    private String uploadPath;

    @Value("${jnclub.dufs.username:}")
    private String dufsUser;

    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    @Value("${jnclub.upload.max-size-mb:10}")
    private long maxSizeMb;

    private final NoteAssetMapper noteAssetMapper;

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/svg+xml", "image/bmp", "image/tiff", "image/x-icon"
    );

    public UploadService(NoteAssetMapper noteAssetMapper) {
        this.noteAssetMapper = noteAssetMapper;
    }

    /**
     * 上传图片到 dufs，返回 Map 包含 url 和 storedKey
     */
    public Map<String, String> uploadImage(MultipartFile file, String userId) throws IOException {
        // 1. 校验 MIME 类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME.contains(contentType)) {
            throw new RuntimeException("不支持的文件类型: " + contentType + "，仅支持常见图片格式");
        }

        // 2. 校验文件大小
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new RuntimeException("文件大小超过限制: " + maxSizeMb + "MB");
        }

        // 3. 生成随机文件名（防覆盖/防枚举）
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = getFileExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString() + ext;
        String relativePath = uploadPath + datePath + "/" + filename;

        // 4. 上传到 dufs（带 Basic Auth）
        String uploadUrl = dufsBaseUrl + relativePath;
        var req = HttpRequest.put(uploadUrl)
                .header("Content-Type", contentType)
                .body(file.getBytes());

        if (dufsUser != null && !dufsUser.isBlank()) {
            String auth = dufsUser + ":" + dufsPass;
            req.header("Authorization", "Basic " +
                    Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
        }

        HttpResponse response = req.execute();

        if (response.getStatus() != 200 && response.getStatus() != 201) {
            log.error("dufs 上传失败: status={}, body={}", response.getStatus(), response.body());
            throw new RuntimeException("图片上传失败，请稍后重试");
        }

        // 5. 构造公网 URL（前端直接引用，无内网地址泄露）
        String publicUrl = "/api/files" + relativePath;

        // 6. 审计入库
        NoteAsset asset = new NoteAsset();
        asset.setUserId(userId);
        asset.setOriginalName(file.getOriginalFilename());
        asset.setStoredKey(relativePath);
        asset.setUrl("/api/files" + relativePath);
        asset.setSize(file.getSize());
        asset.setMime(contentType);
        noteAssetMapper.insert(asset);

        Map<String, String> result = new HashMap<>();
        result.put("url", publicUrl);
        result.put("storedKey", relativePath);
        return result;
    }

    /**
     * 获取文件扩展名（小写，默认 .png）
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return ".png";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            return filename.substring(lastDot).toLowerCase();
        }
        return ".png";
    }
}
