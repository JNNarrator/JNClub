package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.ImportService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 数据导入控制器 — 收藏 JSON / 浏览器书签 HTML / 便签 Markdown ZIP
 * mode: merge=合并（目录按名复用+条目去重）/ replace=清空后导入
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /** 收藏 JSON 导入：body { content, mode } */
    @PostMapping("/bookmarks")
    public R<Map<String, Object>> importBookmarks(@RequestBody Map<String, String> body) {
        return R.ok(importService.importBookmarks(body.get("content"), body.getOrDefault("mode", "merge")));
    }

    /** 浏览器书签 HTML 导入：body { content, mode } */
    @PostMapping("/bookmarks/html")
    public R<Map<String, Object>> importBookmarkHtml(@RequestBody Map<String, String> body) {
        return R.ok(importService.importBookmarkHtml(body.get("content"), body.getOrDefault("mode", "merge")));
    }

    /** 便签 Markdown ZIP 导入：multipart { file, mode } */
    @PostMapping("/notes")
    public R<Map<String, Object>> importNotesZip(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(defaultValue = "merge") String mode) throws IOException {
        return R.ok(importService.importNotesZip(file.getBytes(), mode));
    }
}
