package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.service.DirectoryService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 目录控制器 — 收藏夹/便签共用
 */
@RestController
@RequestMapping("/api/directories")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;

    /**
     * 获取目录树，type=1 收藏夹目录  type=2 便签目录  不传则获取全部
     */
    @GetMapping
    public R<List<Directory>> getDirectoryTree(@RequestParam(required = false) Integer type) {
        return R.ok(directoryService.getDirectoryTree(type));
    }

    /**
     * 创建目录
     */
    @PostMapping
    public R<Directory> createDirectory(@RequestBody Directory directory) {
        return R.ok(directoryService.createDirectory(directory));
    }

    /**
     * 重命名目录
     */
    @PutMapping("/{id}")
    public R<Void> renameDirectory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        directoryService.renameDirectory(id, body.get("name"), body.get("icon"));
        return R.ok();
    }

    /**
     * 获取目录下内容数量（删除前二次确认用）
     */
    @GetMapping("/{id}/content-count")
    public R<Map<String, Long>> getContentCount(@PathVariable Long id) {
        return R.ok(directoryService.getContentCount(id));
    }

    /**
     * 删除目录（级联删除子目录，内容迁移到父目录）
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteDirectory(@PathVariable Long id) {
        directoryService.deleteDirectory(id);
        return R.ok();
    }

    /**
     * 批量更新排序
     */
    @PutMapping("/sort")
    public R<Void> updateSortOrder(@RequestBody List<Map<String, Object>> sortList) {
        directoryService.updateSortOrder(sortList);
        return R.ok();
    }
}
