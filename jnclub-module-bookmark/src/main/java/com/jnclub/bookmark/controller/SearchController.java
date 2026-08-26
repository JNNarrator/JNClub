package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.SearchHistory;
import com.jnclub.bookmark.service.SearchHistoryService;
import com.jnclub.bookmark.service.SearchService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索控制器 — 跨模块聚合搜索 + 服务端历史 + 建议
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchHistoryService searchHistoryService;

    /** 跨模块搜索：keyword 必填，limit 可选（默认 20，最大 50） */
    @GetMapping
    public R<Map<String, Object>> search(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(searchService.search(keyword, limit));
    }

    /** 最近搜索历史 */
    @GetMapping("/history")
    public R<List<SearchHistory>> history(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(searchHistoryService.recent(limit));
    }

    /** 记录一次搜索（去重，最多保留 50 条） */
    @PostMapping("/history")
    public R<Void> record(@RequestParam String keyword) {
        searchHistoryService.record(keyword);
        return R.ok();
    }

    /** 清空搜索历史 */
    @DeleteMapping("/history")
    public R<Void> clearHistory() {
        searchHistoryService.clear();
        return R.ok();
    }

    /** 搜索建议：历史匹配 + 可搜索分组提示 */
    @GetMapping("/suggest")
    public R<Map<String, Object>> suggest(@RequestParam(defaultValue = "") String keyword) {
        String kw = keyword.trim().toLowerCase();
        List<SearchHistory> all = searchHistoryService.recent(20);
        List<Map<String, Object>> history = new ArrayList<>();
        for (SearchHistory h : all) {
            if (kw.isEmpty() || h.getKeyword().toLowerCase().contains(kw)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("keyword", h.getKeyword());
                history.add(m);
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        addGroup(groups, "bookmarks", "收藏");
        addGroup(groups, "notes", "便签");
        addGroup(groups, "files", "云盘");
        addGroup(groups, "vault", "密码库");
        addGroup(groups, "todos", "待办");
        addGroup(groups, "readLater", "稍后读");
        addGroup(groups, "feeds", "订阅");
        addGroup(groups, "feedItems", "文章");
        addGroup(groups, "tracks", "音乐");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("history", history);
        result.put("groups", groups);
        result.put("commands", List.of(
                Map.of("key", "go.todos", "label", "待办清单"),
                Map.of("key", "go.feeds", "label", "RSS 订阅"),
                Map.of("key", "note.new", "label", "新建便签"),
                Map.of("key", "bookmark.new", "label", "新建收藏")
        ));
        return R.ok(result);
    }

    private void addGroup(List<Map<String, Object>> groups, String key, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        groups.add(m);
    }
}
