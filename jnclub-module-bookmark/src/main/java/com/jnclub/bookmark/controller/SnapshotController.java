package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.SnapshotService;
import com.jnclub.common.model.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * 收藏网页快照控制器 — 归档 / 元信息 / 内容 / 删除
 * 位于 /api/** 下，Sa-Token 默认拦截（需登录）
 */
@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    /** 抓取并归档快照（幂等：覆盖旧快照） */
    @PostMapping("/{bookmarkId}")
    public R<Map<String, Object>> capture(@PathVariable Long bookmarkId) {
        return R.ok(snapshotService.capture(bookmarkId));
    }

    /** 快照元信息 */
    @GetMapping("/{bookmarkId}")
    public R<Map<String, Object>> get(@PathVariable Long bookmarkId) {
        return R.ok(snapshotService.get(bookmarkId));
    }

    /** 快照内容（HTML，登录鉴权） */
    @GetMapping("/{bookmarkId}/content")
    public void content(@PathVariable Long bookmarkId, HttpServletResponse response) throws IOException {
        byte[] bytes = snapshotService.content(bookmarkId);
        response.setContentType("text/html; charset=utf-8");
        response.setContentLength(bytes.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
        }
    }

    /** 删除快照（dufs 对象 + 记录；幂等） */
    @DeleteMapping("/{bookmarkId}")
    public R<Void> delete(@PathVariable Long bookmarkId) {
        snapshotService.delete(bookmarkId);
        return R.ok();
    }
}
