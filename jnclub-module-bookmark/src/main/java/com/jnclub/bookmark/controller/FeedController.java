package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.FeedService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RSS 阅读器控制器 — 订阅源 / 条目 / 已读星标 / 一键收藏
 */
@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /** 添加订阅源：body { url } */
    @PostMapping
    public R<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        String url = body.get("url") == null ? null : String.valueOf(body.get("url"));
        return R.ok(feedService.addFeed(url));
    }

    /** 我的订阅源列表（附未读数） */
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(feedService.listFeeds());
    }

    /** 删除订阅源（级联条目） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        feedService.deleteFeed(id);
        return R.ok();
    }

    /** 手动刷新单个源 */
    @PostMapping("/{id}/fetch")
    public R<Map<String, Object>> fetch(@PathVariable Long id) {
        return R.ok(feedService.fetchFeed(id));
    }

    /** 条目列表：?feedId=&filter=all|unread|starred&page=&size= */
    @GetMapping("/items")
    public R<Map<String, Object>> items(@RequestParam(required = false) Long feedId,
                                        @RequestParam(defaultValue = "all") String filter,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "30") int size) {
        return R.ok(feedService.listItems(feedId, filter, page, size));
    }

    /** 标记已读 */
    @PutMapping("/items/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        feedService.markRead(id);
        return R.ok();
    }

    /** 全部已读：body { feedId? } */
    @PutMapping("/read-all")
    public R<Map<String, Object>> markAllRead(@RequestBody Map<String, Object> body) {
        Long feedId = body.get("feedId") == null ? null : Long.parseLong(String.valueOf(body.get("feedId")));
        int n = feedService.markAllRead(feedId);
        return R.ok(Map.of("marked", n));
    }

    /** 切换星标 */
    @PutMapping("/items/{id}/star")
    public R<Void> toggleStar(@PathVariable Long id) {
        feedService.toggleStar(id);
        return R.ok();
    }

    /** 一键收藏到收藏夹：body { directoryId } */
    @PostMapping("/items/{id}/to-bookmark")
    public R<Map<String, Object>> toBookmark(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long directoryId = body.get("directoryId") == null ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        return R.ok(feedService.itemToBookmark(id, directoryId));
    }

    /** 全部未读数（导航角标） */
    @GetMapping("/unread-total")
    public R<Map<String, Object>> unreadTotal() {
        return R.ok(Map.of("total", feedService.unreadTotal()));
    }
}
