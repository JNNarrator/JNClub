package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.UserPreferenceService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户偏好控制器 — 通用 KV（JSON 值）
 * GET 获取当前用户全部偏好；PUT 批量 upsert（[{key, value}]，value 任意 JSON）
 */
@RestController
@RequestMapping("/api/user-preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public R<Map<String, Object>> getAll() {
        return R.ok(userPreferenceService.getAllMap());
    }

    @PutMapping
    public R<Void> batchUpsert(@RequestBody List<Map<String, Object>> prefs) {
        userPreferenceService.batchUpsert(prefs);
        return R.ok();
    }
}
