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
import java.nio.charset.StandardCharsets;

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
    public void proxyFile(HttpServletRequest request, HttpServletResponse response) {
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

        try {
            HttpResponse dufsResp = req.execute();
            if (dufsResp.getStatus() == 200) {
                response.setContentType(guessType(path));
                response.setHeader("Cache-Control", "public, max-age=86400");
                response.getOutputStream().write(dufsResp.bodyBytes());
            } else {
                log.warn("dufs {}: {}", dufsResp.getStatus(), dufsUrl);
                response.sendError(404);
            }
        } catch (IOException e) {
            log.error("代理失败: {}", dufsUrl, e);
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
        return "application/octet-stream";
    }
}
