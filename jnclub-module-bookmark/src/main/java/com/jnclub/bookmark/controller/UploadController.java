package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.UploadService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传图片
     */
    @PostMapping("/image")
    public R<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        String url = uploadService.uploadImage(file);
        
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        
        return R.ok(result);
    }
}
