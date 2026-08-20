package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Tag;
import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 数据导出服务 — 收藏 JSON / 便签 Markdown ZIP / 全量备份 ZIP
 * 均为当前用户数据，含目录结构、标签、云盘文件清单
 */
@Slf4j
@Service
public class ExportService {

    private final BookmarkService bookmarkService;
    private final NoteService noteService;
    private final DirectoryService directoryService;
    private final TagService tagService;
    private final CloudDiskService cloudDiskService;

    public ExportService(BookmarkService bookmarkService,
                         NoteService noteService,
                         DirectoryService directoryService,
                         TagService tagService,
                         CloudDiskService cloudDiskService) {
        this.bookmarkService = bookmarkService;
        this.noteService = noteService;
        this.directoryService = directoryService;
        this.tagService = tagService;
        this.cloudDiskService = cloudDiskService;
    }

    /** 导出收藏（JSON）：目录（扁平含 parentId，供导入无损还原层级）+ 收藏（含标签） */
    public String exportBookmarksJson() {
        String userId = StpUtil.getLoginIdAsString();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("app", "JNClub");
            payload.put("type", "bookmarks");
            payload.put("exportedAt", LocalDate.now().toString());
            payload.put("userId", userId);
            payload.put("directories", directoryService.list(new LambdaQueryWrapper<Directory>()
                    .eq(Directory::getUserId, userId)
                    .eq(Directory::getType, 1)
                    .orderByAsc(Directory::getSortOrder)));
            payload.put("bookmarks", buildBookmarksWithTags(userId));
            return JSONUtil.toJsonPrettyStr(payload);
        } catch (Exception e) {
            throw new BizException("收藏导出失败: " + e.getMessage());
        }
    }

    /** 导出便签（ZIP）：每篇一个 .md，文件名 = 序号_标题.md */
    public byte[] exportNotesZip() {
        String userId = StpUtil.getLoginIdAsString();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 便签目录
            List<Directory> dirs = directoryService.list(new LambdaQueryWrapper<Directory>()
                    .eq(Directory::getUserId, userId)
                    .eq(Directory::getType, 2));
            Map<Long, String> dirName = new HashMap<>();
            for (Directory d : dirs) dirName.put(d.getId(), safeName(d.getName()));

            List<Note> notes = noteService.list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .eq(Note::getArchived, 0)
                    .orderByAsc(Note::getDirectoryId));

            int index = 0;
            for (Note n : notes) {
                index++;
                String title = n.getTitle() == null || n.getTitle().isBlank() ? "无标题" : n.getTitle().trim();
                String safeTitle = safeName(title);
                String dir = dirName.getOrDefault(n.getDirectoryId(), "未分类");
                String filename = String.format("%03d_%s.md", index, safeTitle);
                StringBuilder sb = new StringBuilder();
                sb.append("---\n");
                sb.append("title: ").append(title).append("\n");
                sb.append("directory: ").append(dir).append("\n");
                sb.append("createTime: ").append(n.getCreateTime()).append("\n");
                sb.append("updateTime: ").append(n.getUpdateTime()).append("\n");
                sb.append("---\n\n");
                sb.append(n.getContent() == null ? "" : n.getContent());
                zos.putNextEntry(new ZipEntry(filename));
                zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BizException("便签导出失败: " + e.getMessage());
        }
    }

    /** 全量备份（ZIP）：bookmarks.json + notes/*.md + files-manifest.json + summary.json */
    public byte[] exportAllZip() {
        String userId = StpUtil.getLoginIdAsString();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. 收藏 JSON
            zos.putNextEntry(new ZipEntry("bookmarks.json"));
            zos.write(exportBookmarksJson().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. 便签 Markdown（目录分组）
            List<Directory> dirs = directoryService.list(new LambdaQueryWrapper<Directory>()
                    .eq(Directory::getUserId, userId)
                    .eq(Directory::getType, 2));
            Map<Long, String> dirName = new HashMap<>();
            for (Directory d : dirs) dirName.put(d.getId(), safeName(d.getName()));
            List<Note> notes = noteService.list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .eq(Note::getArchived, 0)
                    .orderByAsc(Note::getDirectoryId));
            int index = 0;
            for (Note n : notes) {
                index++;
                String title = n.getTitle() == null || n.getTitle().isBlank() ? "无标题" : n.getTitle().trim();
                String safeTitle = safeName(title);
                String dir = dirName.getOrDefault(n.getDirectoryId(), "未分类");
                String path = "notes/" + dir + "/" + String.format("%03d_%s.md", index, safeTitle);
                StringBuilder sb = new StringBuilder();
                sb.append("---\ntitle: ").append(title)
                        .append("\ndirectory: ").append(dir)
                        .append("\ncreateTime: ").append(n.getCreateTime())
                        .append("\nupdateTime: ").append(n.getUpdateTime())
                        .append("\n---\n\n")
                        .append(n.getContent() == null ? "" : n.getContent());
                zos.putNextEntry(new ZipEntry(path));
                zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // 3. 云盘文件清单
            List<FileRecord> files = cloudDiskService.listAllByUser(userId);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("type", "files-manifest");
            manifest.put("count", files.size());
            List<Map<String, Object>> fileList = new ArrayList<>();
            for (FileRecord f : files) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", f.getId());
                m.put("originalName", f.getOriginalName());
                m.put("directoryId", f.getDirectoryId());
                m.put("size", f.getSize());
                m.put("mime", f.getMime());
                m.put("createTime", String.valueOf(f.getCreateTime()));
                fileList.add(m);
            }
            manifest.put("files", fileList);
            zos.putNextEntry(new ZipEntry("files-manifest.json"));
            zos.write(JSONUtil.toJsonPrettyStr(manifest).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. 汇总统计
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("app", "JNClub");
            summary.put("type", "full-backup");
            summary.put("exportedAt", LocalDate.now().toString());
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("bookmarks", bookmarkService.count(new LambdaQueryWrapper<Bookmark>()
                    .eq(Bookmark::getUserId, userId).eq(Bookmark::getDeleted, 0)));
            stats.put("notes", notes.size());
            stats.put("files", files.size());
            summary.put("stats", stats);
            zos.putNextEntry(new ZipEntry("summary.json"));
            zos.write(JSONUtil.toJsonPrettyStr(summary).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BizException("全量备份导出失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildBookmarksWithTags(String userId) {
        List<Bookmark> bookmarks = bookmarkService.list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0)
                .orderByAsc(Bookmark::getDirectoryId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Bookmark b : bookmarks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("title", b.getTitle());
            m.put("url", b.getUrl());
            m.put("icon", b.getIcon());
            m.put("directoryId", b.getDirectoryId());
            m.put("createTime", String.valueOf(b.getCreateTime()));
            List<Tag> tags = tagService.listTagsOfRef("bookmark", b.getId());
            m.put("tags", tags.stream().map(Tag::getName).toList());
            result.add(m);
        }
        return result;
    }

    /** 文件名安全化：去掉非法字符 */
    private String safeName(String name) {
        if (name == null || name.isBlank()) return "未命名";
        return name.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_").trim();
    }
}
