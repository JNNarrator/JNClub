package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Vault;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.TagMapper;
import com.jnclub.bookmark.mapper.VaultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据看板服务 — 概览页聚合统计（全部按当前用户过滤）
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final BookmarkMapper bookmarkMapper;
    private final NoteMapper noteMapper;
    private final FileMapper fileMapper;
    private final VaultMapper vaultMapper;
    private final TagMapper tagMapper;
    private final DirectoryMapper directoryMapper;

    /** 概览摘要：数量 / 磁盘 / 最近动态 / 密码库指纹健康 */
    public Map<String, Object> summary() {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("counts", counts(userId));
        result.put("disk", disk(userId));
        result.put("recent", recent(userId));
        result.put("vault", vaultHealth(userId));
        return result;
    }

    /** 各模块数量 + 回收站待清理数 */
    private Map<String, Object> counts(String userId) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("bookmarks", bookmarkMapper.selectCount(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId).eq(Bookmark::getDeleted, 0)));
        c.put("notes", noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId).eq(Note::getDeleted, 0)));
        c.put("files", fileMapper.selectCount(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId).eq(FileRecord::getDeleted, 0)));
        c.put("vault", vaultMapper.selectCount(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId).eq(Vault::getDeleted, 0)));
        c.put("tags", tagMapper.selectCount(new LambdaQueryWrapper<com.jnclub.bookmark.entity.Tag>()
                .eq(com.jnclub.bookmark.entity.Tag::getUserId, userId)));

        Map<String, Object> recycle = new LinkedHashMap<>();
        recycle.put("bookmark", bookmarkMapper.selectCount(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId).eq(Bookmark::getDeleted, 1)));
        recycle.put("note", noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId).eq(Note::getDeleted, 1)));
        recycle.put("file", fileMapper.selectCount(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId).eq(FileRecord::getDeleted, 1)));
        recycle.put("vault", vaultMapper.selectCount(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId).eq(Vault::getDeleted, 1)));
        c.put("recycle", recycle);
        return c;
    }

    /** 云盘磁盘占用：总量 + 按目录分布 */
    private Map<String, Object> disk(String userId) {
        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId).eq(FileRecord::getDeleted, 0));
        long totalSize = 0;
        Map<Long, long[]> perDir = new HashMap<>(); // dirId -> [count, size]
        for (FileRecord f : files) {
            totalSize += f.getSize() == null ? 0 : f.getSize();
            long[] agg = perDir.computeIfAbsent(f.getDirectoryId(), k -> new long[2]);
            agg[0]++;
            agg[1] += f.getSize() == null ? 0 : f.getSize();
        }

        List<Map<String, Object>> byDirectory = new ArrayList<>();
        if (!perDir.isEmpty()) {
            List<Directory> dirs = directoryMapper.selectList(new LambdaQueryWrapper<Directory>()
                    .eq(Directory::getUserId, userId).eq(Directory::getType, 3));
            Map<Long, String> nameOf = new HashMap<>();
            for (Directory d : dirs) nameOf.put(d.getId(), d.getName());
            perDir.forEach((dirId, agg) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("directoryId", dirId);
                m.put("name", nameOf.getOrDefault(dirId, "未知目录"));
                m.put("count", agg[0]);
                m.put("size", agg[1]);
                byDirectory.add(m);
            });
            byDirectory.sort((a, b) -> Long.compare((Long) b.get("size"), (Long) a.get("size")));
        }

        Map<String, Object> disk = new LinkedHashMap<>();
        disk.put("totalSize", totalSize);
        disk.put("fileCount", files.size());
        disk.put("byDirectory", byDirectory);
        return disk;
    }

    /** 最近动态：各模块最近 5 条 */
    private Map<String, Object> recent(String userId) {
        List<Map<String, Object>> bookmarks = new ArrayList<>();
        bookmarkMapper.selectList(new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, userId).eq(Bookmark::getDeleted, 0)
                        .orderByDesc(Bookmark::getCreateTime).last("LIMIT 5"))
                .forEach(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("title", b.getTitle());
                    m.put("url", b.getUrl());
                    m.put("createTime", b.getCreateTime());
                    bookmarks.add(m);
                });

        List<Map<String, Object>> notes = new ArrayList<>();
        noteMapper.selectList(new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId).eq(Note::getDeleted, 0)
                        .orderByDesc(Note::getCreateTime).last("LIMIT 5"))
                .forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("createTime", n.getCreateTime());
                    notes.add(m);
                });

        List<Map<String, Object>> files = new ArrayList<>();
        fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                        .eq(FileRecord::getUserId, userId).eq(FileRecord::getDeleted, 0)
                        .orderByDesc(FileRecord::getCreateTime).last("LIMIT 5"))
                .forEach(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("originalName", f.getOriginalName());
                    m.put("size", f.getSize());
                    m.put("createTime", f.getCreateTime());
                    files.add(m);
                });

        Map<String, Object> recent = new LinkedHashMap<>();
        recent.put("bookmarks", bookmarks);
        recent.put("notes", notes);
        recent.put("files", files);
        return recent;
    }

    /** 密码库指纹健康（不解密）：条目数 + 重复指纹数 */
    private Map<String, Object> vaultHealth(String userId) {
        List<Vault> vaults = vaultMapper.selectList(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId).eq(Vault::getDeleted, 0));
        Set<String> seen = new HashSet<>();
        int duplicate = 0;
        for (Vault v : vaults) {
            if (v.getPasswordFingerprint() == null || v.getPasswordFingerprint().isBlank()) continue;
            if (!seen.add(v.getPasswordFingerprint())) duplicate++;
        }
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("entries", vaults.size());
        health.put("duplicateCount", duplicate);
        return health;
    }
}
