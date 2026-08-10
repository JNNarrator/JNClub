package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.RecycleService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 回收站控制器 — 软删除条目查看 / 恢复 / 永久删除 / 清空
 */
@RestController
@RequestMapping("/api/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    /** 列出某类型回收站条目（type: bookmark|note|file） */
    @GetMapping
    public R<List<?>> list(@RequestParam String type) {
        return R.ok(recycleService.list(type));
    }

    /** 恢复条目：body = {type, id} */
    @PostMapping("/restore")
    public R<Void> restore(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        Long id = body.get("id") == null ? null : Long.parseLong(String.valueOf(body.get("id")));
        recycleService.restore(type, id);
        return R.ok();
    }

    /** 永久删除条目：body = {type, id} */
    @DeleteMapping("/{type}/{id}")
    public R<Void> purge(@PathVariable String type, @PathVariable Long id) {
        recycleService.purge(type, id);
        return R.ok();
    }

    /** 清空某类型回收站 */
    @DeleteMapping("/clear")
    public R<Integer> clear(@RequestParam String type) {
        return R.ok(recycleService.clear(type));
    }
}
