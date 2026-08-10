package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.SearchService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 全局搜索控制器 — 收藏/便签/云盘 跨模块聚合搜索
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** 跨模块搜索：keyword 必填，limit 可选（默认 20，最大 50） */
    @GetMapping
    public R<Map<String, Object>> search(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(searchService.search(keyword, limit));
    }
}
