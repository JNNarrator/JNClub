package com.jnclub.bookmark.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.jnclub.bookmark.entity.WebDavServer;
import com.jnclub.bookmark.service.WebDavClient;
import com.jnclub.bookmark.service.WebDavServerService;
import com.jnclub.common.model.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * WebDAV 站点管理控制器
 * <p>
 * 站点台账 CRUD + 简单文件管理：
 * 列目录 / 新建文件夹 / 上传 / 下载 / 删除 / 重命名
 * 走 Sa-Token 登录鉴权（WebMvcConfig 默认拦截，无需放行）
 */
@Slf4j
@RestController
@RequestMapping("/api/webdav")
@RequiredArgsConstructor
public class WebDavController {

    private final WebDavServerService serverService;

    // ============================================================
    // 站点台账 CRUD
    // ============================================================

    /** 我的站点列表（password 不回传） */
    @GetMapping("/servers")
    public R<List<WebDavServer>> listServers() {
        StpUtil.checkLogin();
        return R.ok(serverService.listMine());
    }

    /** 新增站点 */
    @PostMapping("/servers")
    public R<WebDavServer> createServer(@RequestBody WebDavServer server) {
        StpUtil.checkLogin();
        return R.ok(serverService.create(server));
    }

    /** 更新站点（password 传空 = 不变） */
    @PutMapping("/servers/{id}")
    public R<Void> updateServer(@PathVariable Long id, @RequestBody WebDavServer server) {
        StpUtil.checkLogin();
        serverService.update(id, server);
        return R.ok();
    }

    /** 删除站点 */
    @DeleteMapping("/servers/{id}")
    public R<Void> deleteServer(@PathVariable Long id) {
        StpUtil.checkLogin();
        serverService.delete(id);
        return R.ok();
    }

    /** 检测连接：PROPFIND 根目录，成功返回目录项数 */
    @PostMapping("/servers/{id}/test")
    public R<Map<String, Object>> test(@PathVariable Long id) {
        StpUtil.checkLogin();
        int count = serverService.test(id);
        return R.ok(Map.of("ok", true, "items", count));
    }

    // ============================================================
    // 文件管理
    // ============================================================

    /** 列目录：?path= 相对路径（"" 为根目录） */
    @GetMapping("/servers/{id}/list")
    public R<List<WebDavClient.Entry>> list(@PathVariable Long id,
                                            @RequestParam(value = "path", defaultValue = "") String path) {
        StpUtil.checkLogin();
        WebDavClient client = serverService.buildClient(serverService.getOwned(id, StpUtil.getLoginIdAsString()));
        return R.ok(client.list(path));
    }

    /** 新建文件夹：body = { path } */
    @PostMapping("/servers/{id}/mkdir")
    public R<Void> mkdir(@PathVariable Long id, @RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        String path = body.get("path");
        if (path == null || path.isBlank()) {
            throw new com.jnclub.common.exception.BizException("请填写文件夹名称");
        }
        WebDavClient client = serverService.buildClient(serverService.getOwned(id, StpUtil.getLoginIdAsString()));
        client.mkdir(path);
        return R.ok();
    }

    /** 上传文件：multipart（file）+ ?path= 目标目录 */
    @PostMapping("/servers/{id}/upload")
    public R<Void> upload(@PathVariable Long id,
                          @RequestParam("file") MultipartFile file,
                          @RequestParam(value = "path", defaultValue = "") String path) throws IOException {
        StpUtil.checkLogin();
        if (file == null || file.isEmpty()) {
            throw new com.jnclub.common.exception.BizException("请选择要上传的文件");
        }
        String dir = path == null ? "" : path.trim();
        while (dir.startsWith("/")) dir = dir.substring(1);
        while (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
        String filename = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String target = dir.isEmpty() ? filename : dir + "/" + filename;
        WebDavClient client = serverService.buildClient(serverService.getOwned(id, StpUtil.getLoginIdAsString()));
        try (InputStream in = file.getInputStream()) {
            client.upload(target, in);
        }
        return R.ok();
    }

    /** 下载文件：?path= 文件相对路径 */
    @GetMapping("/servers/{id}/download")
    public void download(@PathVariable Long id,
                         @RequestParam("path") String path,
                         HttpServletResponse response) throws IOException {
        StpUtil.checkLogin();
        WebDavClient client = serverService.buildClient(serverService.getOwned(id, StpUtil.getLoginIdAsString()));
        String filename = path;
        int idx = path.lastIndexOf('/');
        if (idx >= 0) filename = path.substring(idx + 1);

        try (InputStream in = client.download(path);
             OutputStream out = response.getOutputStream()) {
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
    }

    /** 删除：DELETE ?path= &isDir= */
    @DeleteMapping("/servers/{id}/delete")
    public R<Void> delete(@PathVariable Long id,
                          @RequestParam("path") String path,
                          @RequestParam(value = "isDir", defaultValue = "false") boolean isDir) {
        StpUtil.checkLogin();
        WebDavClient client = serverService.buildClient(serverService.getOwned(id, StpUtil.getLoginIdAsString()));
        client.delete(path, isDir);
        return R.ok();
    }

    /** 重命名：body = { path, newName }（同一目录内） */
    @PutMapping("/servers/{id}/rename")
    public R<Void> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        StpUtil.checkLogin();
        String path = body.get("path");
        String newName = body.get("newName");
        if (path == null || path.isBlank() || newName == null || newName.isBlank()) {
            throw new com.jnclub.common.exception.BizException("请填写原路径与新名称");
        }
        WebDavClient client = serverService.buildClient(serverService.getOwned(id, StpUtil.getLoginIdAsString()));
        client.rename(path, newName.trim());
        return R.ok();
    }
}
