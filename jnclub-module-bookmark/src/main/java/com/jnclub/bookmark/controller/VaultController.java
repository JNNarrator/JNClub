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

    /** 移动密码条目到其他目录：body { directoryId }（需主密钥解锁） */
    @PutMapping("/{id}/move")
    public R<Void> move(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        vaultService.move(id, directoryId);
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

    // ============================================================
    // 主密钥管理（加密存储核心）
    // ============================================================

    /** 主密钥状态：{ configured, unlocked } */
    @GetMapping("/master-key/status")
    public R<Map<String, Object>> masterKeyStatus() {
        return R.ok(vaultService.masterKeyStatus());
    }

    /** 设置/修改主密钥（已设置需验证旧密钥）→ 全量重加密迁移 */
    @PostMapping("/master-key")
    public R<Void> setMasterKey(@RequestBody Map<String, String> body) {
        vaultService.setMasterKey(body.get("oldMasterKey"), body.get("newMasterKey"));
        return R.ok();
    }

    /** 解锁：输入主密钥，派生密钥解密 keyCheck 校验，内存会话 30 分钟 */
    @PostMapping("/unlock")
    public R<Void> unlock(@RequestBody Map<String, String> body) {
        vaultService.unlock(body.get("masterKey"));
        return R.ok();
    }

    /** 锁定：清除内存密钥 */
    @PostMapping("/lock")
    public R<Void> lock() {
        vaultService.lock();
        return R.ok();
    }

    /** 遗忘重置：双重确认（confirm=RESET + 重置验证码）后清空密码库重新开始 */
    @PostMapping("/reset")
    public R<Void> reset(@RequestBody Map<String, String> body) {
        vaultService.reset(body.get("confirm"), body.get("resetCode"));
        return R.ok();
    }

    /** 健康检查（需解锁）：弱/重复密码列表，仅提示不拦截 */
    @GetMapping("/check-health")
    public R<Map<String, Object>> checkHealth() {
        return R.ok(vaultService.checkHealth());
    }

    /** 保存 / 更新 TOTP 种子（需解锁）：body = { secret } */
    @PutMapping("/{id}/totp")
    public R<Void> saveTotp(@PathVariable Long id, @RequestBody Map<String, String> body) {
        vaultService.saveTotp(id, body.get("secret"));
        return R.ok();
    }

    /** 读取并生成当前 TOTP 验证码（需解锁）：{ totp, remaining } */
    @GetMapping("/{id}/totp")
    public R<Map<String, Object>> getTotp(@PathVariable Long id) {
        return R.ok(vaultService.getTotp(id));
    }

    /** 删除 TOTP（需解锁） */
    @DeleteMapping("/{id}/totp")
    public R<Void> deleteTotp(@PathVariable Long id) {
        vaultService.deleteTotp(id);
        return R.ok();
    }
}
