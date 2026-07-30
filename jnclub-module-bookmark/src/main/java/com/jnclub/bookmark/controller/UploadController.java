package com.jnclub.bookmark.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.jnclub.bookmark.service.UploadService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文件上传控制器 — 需登录鉴权
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传图片（需要登录态）
     * 返回公网 URL，前端 bundle 中不出现 dufs 内网地址
     */
    @PostMapping("/image")
    public R<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (!StpUtil.isLogin()) {
            return R.fail(401, "请先登录");
        }
        String userId = StpUtil.getLoginIdAsString();
        Map<String, String> result = uploadService.uploadImage(file, userId);
        return R.ok(result);
    }
}
