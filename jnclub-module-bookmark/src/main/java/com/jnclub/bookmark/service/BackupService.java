package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.crypto.VaultCrypto;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Tag;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全量备份服务 — 一键导出/恢复全部数据（收藏 + 便签 + 密码库 + 偏好 + 云盘清单）
 * 备份文件为单文件文本：base64(salt):hex(AES(PBKDF2(password), JSON))（与密码库备份同格式）。
 * 云盘文件二进制存于文件服务，备份内仅含清单；恢复时跳过二进制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ExportService exportService;
    private final DirectoryService directoryService;
    private final NoteService noteService;
    private final TagService tagService;
    private final VaultService vaultService;
    private final ImportService importService;
    private final UserPreferenceService userPreferenceService;
    private final CloudDiskService cloudDiskService;

    /** 导出全量加密备份：返回 { fileName, content } */
    public Map<String, Object> exportBackup(String password) {
        if (password == null || password.length() < 8) {
            throw new BizException("备份密码至少 8 位");
        }
        String userId = StpUtil.getLoginIdAsString();

        JSONObject payload = JSONUtil.createObj();
        payload.set("app", "JNClub");
        payload.set("type", "full-backup");
        payload.set("version", 1);
        payload.set("exportedAt", LocalDateTime.now().toString());

        JSONObject data = JSONUtil.createObj();
        // 收藏（含扁平目录与标签，envelope 完整）
        data.set("bookmarks", JSONUtil.parseObj(exportService.exportBookmarksJson()));
        // 便签（含目录/标签/置顶/归档）
        data.set("notes", buildNotesSection(userId));
        // 密码库（明文条目，外层统一加密；需已解锁或旧版未设主密钥）
        data.set("vault", vaultService.buildBackupPayload());
        // 云盘文件清单（仅清单）
        data.set("files", buildFilesSection(userId));
        // 用户偏好
        data.set("preferences", userPreferenceService.getAllMap());
        payload.set("data", data);

        try {
            String json = JSONUtil.toJsonPrettyStr(payload);
            String salt = VaultCrypto.generateSalt();
            byte[] key = VaultCrypto.deriveKey(password, salt, VaultCrypto.PBKDF2_ITERATIONS);
            String cipher = VaultCrypto.encrypt(key, json);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", "jnclub-full-backup-" + LocalDate.now() + ".jncb");
            result.put("content", salt + ":" + cipher);
            return result;
        } catch (Exception e) {
            throw new BizException("全量备份导出失败: " + e.getMessage());
        }
    }

    /**
     * 恢复全量备份：解密 → 校验 → 按 mode 恢复各分区（整体原子事务）。
     *
     * @param mode merge=合并（各分区按各自规则去重）/ replace=先清空对应模块再导入
     * @return 各分区统计 { bookmarks, notes, vault, preferences, files }
     */
    @Transactional
    public Map<String, Object> importBackup(String password, String content, String mode) {
        if (password == null || content == null || content.isBlank()) {
            throw new BizException("缺少备份密码或备份内容");
        }
        // 解密
        String json;
        try {
            int idx = content.indexOf(':');
            if (idx <= 0) throw new BizException("备份文件格式不正确");
            String salt = content.substring(0, idx);
            String cipher = content.substring(idx + 1);
            byte[] key = VaultCrypto.deriveKey(password, salt, VaultCrypto.PBKDF2_ITERATIONS);
            json = VaultCrypto.decrypt(key, cipher);
            if (json == null) throw new BizException("备份密码不正确或文件已损坏");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("备份解密失败: " + e.getMessage());
        }

        JSONObject payload;
        try {
            payload = JSONUtil.parseObj(json);
        } catch (Exception e) {
            throw new BizException("备份内容解析失败");
        }
        if (!"JNClub".equals(payload.getStr("app")) || !"full-backup".equals(payload.getStr("type"))) {
            throw new BizException("不是有效的 JNClub 全量备份文件");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        JSONObject data = payload.containsKey("data") ? payload.getJSONObject("data") : JSONUtil.createObj();

        // 收藏
        JSONObject bm = data.containsKey("bookmarks") ? data.getJSONObject("bookmarks") : null;
        if (bm != null) {
            result.put("bookmarks", importService.importBookmarks(bm.toString(), mode));
        }

        // 便签
        JSONObject nt = data.containsKey("notes") ? data.getJSONObject("notes") : null;
        if (nt != null) {
            result.put("notes", importService.importNotesJson(nt.toString(), mode));
        }

        // 密码库（需要解锁或旧版未设主密钥）
        JSONObject vt = data.containsKey("vault") ? data.getJSONObject("vault") : null;
        if (vt != null) {
            result.put("vault", vaultService.restoreVaultPayload(vt, mode));
        }

        // 偏好：仅合并（不做 replace 清空）
        JSONObject prefs = data.containsKey("preferences") ? data.getJSONObject("preferences") : null;
        if (prefs != null && !prefs.isEmpty()) {
            List<Map<String, Object>> upsert = new ArrayList<>();
            prefs.forEach((k, v) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", k);
                item.put("value", v);
                upsert.add(item);
            });
            userPreferenceService.batchUpsert(upsert);
            result.put("preferences", upsert.size());
        }

        // 云盘清单（参考信息，二进制不随备份恢复）
        JSONArray files = data.containsKey("files") ? data.getJSONArray("files") : new JSONArray();
        result.put("files", files.size());
        return result;
    }

    /** 便签分区：扁平目录列表（importNotesJson 期望扁平结构）+ 便签（含标签/置顶/归档） */
    private JSONObject buildNotesSection(String userId) {
        JSONObject section = JSONUtil.createObj();
        section.set("app", "JNClub");
        section.set("type", "notes");
        section.set("directories", directoryService.list(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId)
                .eq(Directory::getType, 2)
                .orderByAsc(Directory::getSortOrder)));
        List<Note> notes = noteService.list(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getDeleted, 0)
                .orderByAsc(Note::getDirectoryId));
        JSONArray notesArr = new JSONArray();
        for (Note n : notes) {
            JSONObject m = JSONUtil.createObj();
            m.set("title", n.getTitle());
            m.set("content", n.getContent());
            m.set("directoryId", n.getDirectoryId());
            m.set("sortOrder", n.getSortOrder());
            m.set("pinned", n.getPinned());
            m.set("archived", n.getArchived());
            m.set("createTime", String.valueOf(n.getCreateTime()));
            List<Tag> tags = tagService.listTagsOfRef("note", n.getId());
            JSONArray tagNames = new JSONArray();
            for (Tag t : tags) tagNames.add(t.getName());
            m.set("tags", tagNames);
            notesArr.add(m);
        }
        section.set("notes", notesArr);
        return section;
    }

    /** 云盘文件清单分区 */
    private JSONArray buildFilesSection(String userId) {
        List<FileRecord> files = cloudDiskService.listAllByUser(userId);
        JSONArray arr = new JSONArray();
        for (FileRecord f : files) {
            JSONObject m = JSONUtil.createObj();
            m.set("id", f.getId());
            m.set("originalName", f.getOriginalName());
            m.set("directoryId", f.getDirectoryId());
            m.set("size", f.getSize());
            m.set("mime", f.getMime());
            m.set("createTime", String.valueOf(f.getCreateTime()));
            arr.add(m);
        }
        return arr;
    }
}
