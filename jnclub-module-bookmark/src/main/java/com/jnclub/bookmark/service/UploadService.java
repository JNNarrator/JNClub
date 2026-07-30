package com.jnclub.bookmark.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传服务
 */
@Slf4j
@Service
public class UploadService {

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.upload-path}")
    private String uploadPath;

    /**
     * 上传图片到 dufs
     */
    public String uploadImage(MultipartFile file) throws IOException {
        // 生成文件路径
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = getFileExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString() + ext;
        String fullPath = uploadPath + datePath + "/" + filename;

        // 上传到 dufs
        String uploadUrl = dufsBaseUrl + fullPath;
        
        HttpResponse response = HttpRequest.put(uploadUrl)
                .header("Content-Type", file.getContentType())
                .body(file.getBytes())
                .execute();

        if (response.getStatus() == 200 || response.getStatus() == 201) {
            return uploadUrl;
        } else {
            log.error("上传失败: {}", response.getStatus());
            throw new RuntimeException("上传失败");
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return ".png";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            return filename.substring(lastDot);
        }
        return ".png";
    }
}
