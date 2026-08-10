package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Vault;
import com.jnclub.bookmark.service.VaultService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 密码库控制器 — 目录(type=5)内密码条目 CRUD
 * 列表不返回密码密文；详情接口解密返回明文
 */
@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    /** 目录内列表（password 置空） */
    @GetMapping
    public R<List<Vault>> list(@RequestParam Long directoryId) {
        return R.ok(vaultService.list(directoryId));
    }

    /** 详情（解密返回密码明文） */
    @GetMapping("/{id}")
    public R<Vault> getDetail(@PathVariable Long id) {
        return R.ok(vaultService.getDetail(id));
    }

    /** 新建 */
    @PostMapping
    public R<Vault> create(@RequestBody Vault vault) {
        return R.ok(vaultService.create(vault));
    }

    /** 编辑（password 传空 = 不变） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Vault vault) {
        vaultService.update(id, vault);
        return R.ok();
    }

    /** 删除（软删除，进入回收站） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        vaultService.delete(id);
        return R.ok();
    }

    /** 批量更新排序 */
    @PutMapping("/sort")
    public R<Void> updateSortOrder(@RequestBody List<Map<String, Object>> sortList) {
        vaultService.updateSortOrder(sortList);
        return R.ok();
    }
}
