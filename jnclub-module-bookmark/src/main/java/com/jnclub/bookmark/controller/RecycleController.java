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

    /** 列出某类型回收站条目（type: bookmark|note|file|vault）；传 page/size 时分页 */
    @GetMapping
    public R<Object> list(@RequestParam String type,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            return R.ok(recycleService.pageList(type, page, size));
        }
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

    /** 获取自动清理配置（保留天数） */
    @GetMapping("/config")
    public R<Map<String, Integer>> config() {
        return R.ok(Map.of("keepDays", recycleService.getEffectiveKeepDays()));
    }

    /** 更新自动清理配置（保留天数，7~180） */
    @PutMapping("/config")
    public R<Void> updateConfig(@RequestBody Map<String, Object> body) {
        Object v = body.get("keepDays");
        if (v == null) throw new com.jnclub.common.exception.BizException("keepDays 必填");
        recycleService.updateKeepDays(Integer.parseInt(String.valueOf(v)));
        return R.ok();
    }

    /** 手动立即清理（返回各类型清理计数） */
    @PostMapping("/clean")
    public R<Map<String, Integer>> clean() {
        return R.ok(recycleService.cleanNow());
    }
}
