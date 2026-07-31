package com.jnclub.bookmark.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.jnclub.bookmark.service.AssetCleanService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 图片资产清理控制器
 * <p>
 * POST /api/admin/assets/clean?minAgeDays=7 — 手动清理孤儿图片（需登录）
 * GET  /api/admin/assets/orphans?minAgeDays=7  — 预览孤儿数量（不删除，需登录）
 */
@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
public class AssetCleanController {

    private final AssetCleanService assetCleanService;

    /**
     * 手动清理（minAgeDays = 0 立即清理全部孤儿）
     */
    @PostMapping("/clean")
    public R<Map<String, Object>> clean(
            @RequestParam(defaultValue = "0") int minAgeDays
    ) {
        if (!StpUtil.isLogin()) {
            return R.fail(401, "请先登录");
        }
        if (minAgeDays < 0) {
            return R.fail(400, "minAgeDays 不能为负数");
        }
        try {
            Map<String, Object> result = assetCleanService.cleanOrphans(minAgeDays);
            return R.ok(result);
        } catch (Exception e) {
            return R.fail(500, "清理失败：" + e.getMessage());
        }
    }

    /**
     * 预览孤儿数量（不执行删除）
     */
    @GetMapping("/orphans")
    public R<Map<String, Object>> countOrphans(
            @RequestParam(defaultValue = "0") int minAgeDays
    ) {
        if (!StpUtil.isLogin()) {
            return R.fail(401, "请先登录");
        }
        try {
            Map<String, Object> result = assetCleanService.countOrphans(minAgeDays);
            return R.ok(result);
        } catch (Exception e) {
            return R.fail(500, "查询失败：" + e.getMessage());
        }
    }
}
