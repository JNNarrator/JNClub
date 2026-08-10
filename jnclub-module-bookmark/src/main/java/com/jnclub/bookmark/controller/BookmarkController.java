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
}
