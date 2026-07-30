package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.service.DirectoryService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 目录控制器
 */
@RestController
@RequestMapping("/api/directories")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;

    /**
     * 获取目录树
     */
    @GetMapping
    public R<List<Directory>> getDirectoryTree() {
        return R.ok(directoryService.getDirectoryTree());
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
        directoryService.renameDirectory(id, body.get("name"));
        return R.ok();
    }

    /**
     * 删除目录
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
