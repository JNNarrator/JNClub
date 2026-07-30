package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.service.BookmarkService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 网页收藏控制器
 */
@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    /**
     * 获取目录下的收藏列表
     */
    @GetMapping
    public R<List<Bookmark>> getBookmarks(@RequestParam Long directoryId) {
        return R.ok(bookmarkService.getBookmarks(directoryId));
    }

    /**
     * 添加收藏
     */
    @PostMapping
    public R<Bookmark> addBookmark(@RequestBody Bookmark bookmark) {
        return R.ok(bookmarkService.addBookmark(bookmark));
    }

    /**
     * 编辑收藏
     */
    @PutMapping("/{id}")
    public R<Void> updateBookmark(@PathVariable Long id, @RequestBody Bookmark bookmark) {
        bookmarkService.updateBookmark(id, bookmark);
        return R.ok();
    }

    /**
     * 删除收藏
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteBookmark(@PathVariable Long id) {
        bookmarkService.deleteBookmark(id);
        return R.ok();
    }

    /**
     * 批量更新排序
     */
    @PutMapping("/sort")
    public R<Void> updateSortOrder(@RequestBody List<Map<String, Object>> sortList) {
        bookmarkService.updateSortOrder(sortList);
        return R.ok();
    }
}
