package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 数据导出控制器 — 收藏 JSON / 便签 Markdown ZIP / 全量备份 ZIP
 * 下载文件名带日期，Content-Disposition 处理中文
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /** 导出收藏（JSON 下载） */
    @GetMapping("/bookmarks")
    public void exportBookmarks(HttpServletResponse response) throws Exception {
        String json = exportService.exportBookmarksJson();
        writeFile(response, "jnclub-bookmarks-" + LocalDate.now() + ".json",
                "application/json;charset=UTF-8", json.getBytes(StandardCharsets.UTF_8));
    }

    /** 导出便签（ZIP：每篇一个 Markdown） */
    @GetMapping("/notes")
    public void exportNotes(HttpServletResponse response) throws Exception {
        byte[] zip = exportService.exportNotesZip();
        writeFile(response, "jnclub-notes-" + LocalDate.now() + ".zip",
                "application/zip", zip);
    }

    /** 全量备份（ZIP：收藏 JSON + 便签 Markdown + 云盘清单 + 统计） */
    @GetMapping("/all")
    public void exportAll(@RequestParam(defaultValue = "false") boolean includeFiles,
                          HttpServletResponse response) throws Exception {
        byte[] zip = exportService.exportAllZip();
        writeFile(response, "jnclub-backup-" + LocalDate.now() + ".zip",
                "application/zip", zip);
    }

    private void writeFile(HttpServletResponse response, String filename,
                           String contentType, byte[] data) throws Exception {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }
}
