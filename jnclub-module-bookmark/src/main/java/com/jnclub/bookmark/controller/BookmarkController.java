package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.service.BookmarkService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

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

    @SuppressWarnings("unchecked")
    private List<Long> parseIds(Object raw) {
        if (raw == null) return List.of();
        return ((List<Object>) raw).stream().map(v -> Long.parseLong(String.valueOf(v))).toList();
    }
}
