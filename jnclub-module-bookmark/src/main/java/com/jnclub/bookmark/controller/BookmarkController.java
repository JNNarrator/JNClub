package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.service.BookmarkService;
import com.jnclub.bookmark.service.ReadService;
import com.jnclub.common.model.R;
import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final ReadService readService;

    @GetMapping
    public R<List<Bookmark>> getBookmarks(@RequestParam Long directoryId,
                                          @RequestParam(required = false) Long tagId) {
        return R.ok(bookmarkService.getBookmarks(directoryId, tagId));
    }

    @PostMapping
    public R<Bookmark> addBookmark(@RequestBody Bookmark bookmark) {
        return R.ok(bookmarkService.addBookmark(bookmark));
    }

    /**
     * 预览网页元数据：传入 url，返回标题 + favicon
     */
    @GetMapping("/preview")
    public R<Map<String, String>> preview(@RequestParam String url) {
        Map<String, String> meta = bookmarkService.fetchPageMeta(url);
        return meta != null ? R.ok(meta) : R.fail("无法获取网页信息");
    }

    /**
     * 阅读模式：抓取网页正文（服务端提取 + 清洗），返回 { success, url, title, content }
     */
    @GetMapping("/read")
    public R<JSONObject> read(@RequestParam String url) {
        return R.ok(readService.readArticle(url));
    }

    /**
     * 重复收藏检测：按规范化 URL 分组返回重复组
     */
    @PostMapping("/dedup")
    public R<List<Map<String, Object>>> dedup() {
        return R.ok(bookmarkService.listDuplicates());
    }

    @PutMapping("/{id}")
    public R<Void> updateBookmark(@PathVariable Long id, @RequestBody Bookmark bookmark) {
        bookmarkService.updateBookmark(id, bookmark);
        return R.ok();
    }

    /** 移动收藏到其他目录：body { directoryId } */
    @PutMapping("/{id}/move")
    public R<Void> moveBookmark(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        bookmarkService.moveBookmark(id, directoryId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteBookmark(@PathVariable Long id) {
        bookmarkService.deleteBookmark(id);
        return R.ok();
    }

    @PutMapping("/sort")
    public R<Void> updateSortOrder(@RequestBody List<Map<String, Object>> sortList) {
        bookmarkService.updateSortOrder(sortList);
        return R.ok();
    }

    /** 批量移动：body { ids[], directoryId } */
    @PutMapping("/batch-move")
    public R<Void> batchMove(@RequestBody Map<String, Object> body) {
        List<Long> ids = parseIds(body.get("ids"));
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        bookmarkService.moveBookmarksBatch(ids, directoryId);
        return R.ok();
    }

    /** 批量删除（软删除进回收站）：body { ids[] } */
    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody Map<String, Object> body) {
        bookmarkService.deleteBookmarksBatch(parseIds(body.get("ids")));
        return R.ok();
    }

    /** 批量设置标签（全量覆盖）：body { ids[], tagNames[] } */
    @PutMapping("/batch-tags")
    public R<Void> batchTags(@RequestBody Map<String, Object> body) {
        List<Long> ids = parseIds(body.get("ids"));
        @SuppressWarnings("unchecked")
        List<String> tagNames = (List<String>) body.get("tagNames");
        bookmarkService.setTagsBatch(ids, tagNames);
        return R.ok();
    }

    // ============================================================
    // 收藏失效检测（死链）
    // ============================================================

    /** 检测全部收藏链接可用性（串行+限速，耗时随收藏数增长） */
    @PostMapping("/check-dead")
    public R<Map<String, Object>> checkDeadLinks() {
        return R.ok(bookmarkService.checkDeadLinks());
    }

    /** 失效收藏列表 */
    @GetMapping("/dead")
    public R<List<Map<String, Object>>> listDeadLinks() {
        return R.ok(bookmarkService.listDeadLinks());
    }

    /** 删除失效收藏（真正删除）：body { ids[] } */
    @PostMapping("/delete-dead")
    public R<Map<String, Object>> deleteDeadLinks(@RequestBody Map<String, Object> body) {
        int count = bookmarkService.deleteDeadLinks(parseIds(body.get("ids")));
        return R.ok(Map.of("deleted", count));
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseIds(Object raw) {
        if (raw == null) return List.of();
        return ((List<Object>) raw).stream().map(v -> Long.parseLong(String.valueOf(v))).toList();
    }
}
