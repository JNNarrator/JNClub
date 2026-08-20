package com.jnclub.bookmark.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件只读代理 — 转发到 dufs
 * 支持：图片 / 文本 / PDF / 音视频流式播放（Range 透传 + 206 转发，seek/拖动进度条可用）
 * 大文件用流拷贝（不再整体读入内存）
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileProxyController {

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.username:}")
    private String dufsUser;

    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    @RequestMapping("/**")
    public void proxyFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // uri: /api/files/jnclub/images/2026/07/30/uuid.png
        String uri = request.getRequestURI();
        String path = uri.substring("/api/files/".length());

        String dufsUrl = dufsBaseUrl + "/" + path;

        var req = HttpRequest.get(dufsUrl);
        if (dufsUser != null && !dufsUser.isBlank()) {
            String auth = dufsUser + ":" + dufsPass;
            req.header("Authorization", "Basic " +
                    Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
        }
        // 透传 Range 头（音视频流式播放 / 拖动进度条的关键）
        String range = request.getHeader("Range");
        if (range != null) {
            req.header("Range", range);
        }

        try (HttpResponse dufsResp = req.execute()) {
            int status = dufsResp.getStatus();
            if (status == 200 || status == 206) {
                response.setStatus(status);
                response.setContentType(guessType(path));
                response.setHeader("Accept-Ranges", "bytes");
                response.setHeader("Cache-Control", "public, max-age=86400");
                // 206 部分内容：转发 Content-Range / Content-Length（播放器 seek 必需）
                String contentRange = dufsResp.header("Content-Range");
                if (contentRange != null) {
                    response.setHeader("Content-Range", contentRange);
                }
                String contentLength = dufsResp.header("Content-Length");
                if (contentLength != null) {
                    response.setHeader("Content-Length", contentLength);
                }
                // 流式拷贝，避免大文件占满内存
                try (InputStream in = dufsResp.bodyStream();
                     OutputStream out = response.getOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                }
            } else {
                log.warn("dufs {}: {}", dufsResp.getStatus(), dufsUrl);
                // 不用 sendError（会触发 error dispatch 再次经过 Sa-Token 拦截器导致 500），直接写状态码
                response.setStatus(404);
            }
        } catch (IOException e) {
            // 客户端中断（如拖进度条时取消旧请求）属正常，不打 ERROR
            if (!(e instanceof java.net.SocketException)) {
                log.error("代理失败: {}", dufsUrl, e);
            }
        }
    }

    private String guessType(String path) {
        String l = path.toLowerCase();
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".bmp")) return "image/bmp";
        if (l.endsWith(".ico")) return "image/x-icon";
        if (l.endsWith(".avif")) return "image/avif";
        // 音频
        if (l.endsWith(".mp3")) return "audio/mpeg";
        if (l.endsWith(".m4a") || l.endsWith(".aac")) return "audio/mp4";
        if (l.endsWith(".wav")) return "audio/wav";
        if (l.endsWith(".flac")) return "audio/flac";
        if (l.endsWith(".ogg") || l.endsWith(".oga")) return "audio/ogg";
        if (l.endsWith(".opus")) return "audio/opus";
        // 视频
        if (l.endsWith(".mp4") || l.endsWith(".m4v")) return "video/mp4";
        if (l.endsWith(".webm")) return "video/webm";
        if (l.endsWith(".mov")) return "video/quicktime";
        if (l.endsWith(".mkv")) return "video/x-matroska";
        if (l.endsWith(".avi")) return "video/x-msvideo";
        // 文本类
        if (l.endsWith(".txt") || l.endsWith(".md")) return "text/plain;charset=UTF-8";
        if (l.endsWith(".json")) return "application/json;charset=UTF-8";
        if (l.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
