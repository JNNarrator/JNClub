package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.service.BackupService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 全量备份控制器 — 一键导出/恢复全部数据（加密）
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /** 导出全量加密备份：{ password } → { fileName, content } */
    @PostMapping("/export")
    public R<Map<String, Object>> export(@RequestBody Map<String, String> body) {
        return R.ok(backupService.exportBackup(body.get("password")));
    }

    /** 恢复全量备份：{ content, password, mode } → 各分区统计 */
    @PostMapping("/import")
    public R<Map<String, Object>> importBackup(@RequestBody Map<String, String> body) {
        return R.ok(backupService.importBackup(body.get("password"), body.get("content"), body.get("mode")));
    }
}
