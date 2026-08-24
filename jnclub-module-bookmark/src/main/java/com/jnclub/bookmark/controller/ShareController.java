package com.jnclub.bookmark.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.dev33.satoken.stp.StpUtil;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Share;
import com.jnclub.bookmark.service.ShareService;
import com.jnclub.common.model.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 分享控制器
 * 登录侧：创建 / 列表 / 撤销；公开侧（/api/share/** 外放）：解析内容 / 文件下载
 */
@Slf4j
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;
    @Value("${jnclub.dufs.username:}")
    private String dufsUser;
    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    /** 创建更新分享：body { refType, refId, password?, expiresInDays? } */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        StpUtil.checkLogin();
        String refType = (String) body.get("refType");
        Long refId = body.get("refId") == null ? null : Long.parseLong(String.valueOf(body.get("refId")));
        String password = (String) body.get("password");
        Integer expiresInDays = body.get("expiresInDays") == null
                ? null : Integer.parseInt(String.valueOf(body.get("expiresInDays")));
        return R.ok(shareService.create(refType, refId, password, expiresInDays));
    }

    /** 查询某条目的分享列表 */
    @GetMapping
    public R<List<Share>> list(@RequestParam String refType, @RequestParam Long refId) {
        StpUtil.checkLogin();
        return R.ok(shareService.listByRef(refType, refId));
    }

    /** 我的全部分享（跨类型，含访问统计） */
    @GetMapping("/mine")
    public R<List<Map<String, Object>>> mine() {
        StpUtil.checkLogin();
        return R.ok(shareService.listMine());
    }

    /** 撤销分享 */
    @DeleteMapping("/{token}")
    public R<Void> revoke(@PathVariable String token) {
        StpUtil.checkLogin();
        shareService.revoke(token);
        return R.ok();
    }

    /** 公开：解析分享内容（?pwd= 访问密码） */
    @GetMapping("/{token}")
    public R<Map<String, Object>> resolve(@PathVariable String token,
                                          @RequestParam(required = false) String pwd) {
        return R.ok(shareService.resolve(token, pwd));
    }

    /** 公开：分享文件下载（受密码/有效期门控，字节经此端点而非 /api/files） */
    @GetMapping("/{token}/file")
    public void file(@PathVariable String token,
                     @RequestParam(required = false) String pwd,
                     HttpServletResponse response) throws IOException {
        FileRecord record = shareService.resolveFile(token, pwd);
        String dufsUrl = dufsBaseUrl + "/" + record.getStoredKey();
        var req = HttpRequest.get(dufsUrl);
        if (dufsUser != null && !dufsUser.isBlank()) {
            req.header("Authorization", "Basic " + Base64.encode(
                    (dufsUser + ":" + dufsPass).getBytes(StandardCharsets.UTF_8)));
        }
        HttpResponse dufsResp = req.execute();
        if (dufsResp.getStatus() != 200) {
            log.warn("分享文件 dufs 失败: status={} key={}", dufsResp.getStatus(), record.getStoredKey());
            response.sendError(404);
            return;
        }
        String name = record.getOriginalName() == null ? "download" : record.getOriginalName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(record.getMime() == null ? "application/octet-stream" : record.getMime());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        try (OutputStream out = response.getOutputStream()) {
            out.write(dufsResp.bodyBytes());
        }
    }
}
