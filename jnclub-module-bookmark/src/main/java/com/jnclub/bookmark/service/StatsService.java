package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.entity.Vault;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.TagMapper;
import com.jnclub.bookmark.mapper.TodoMapper;
import com.jnclub.bookmark.mapper.VaultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final TodoMapper todoMapper;

    /** 概览摘要：数量 / 磁盘 / 最近动态 / 密码库指纹健康 / 待办概览 / 稍后读 / 今日行动提醒 */
    public Map<String, Object> summary() {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> counts = counts(userId);
        Map<String, Object> vault = vaultHealth(userId);
        Map<String, Object> todos = todoCounts(userId);
        Map<String, Object> readLater = readLater(userId);
        result.put("counts", counts);
        result.put("disk", disk(userId));
        result.put("recent", recent(userId));
        result.put("vault", vault);
        result.put("todos", todos);
        result.put("readLater", readLater);
        result.put("alerts", buildAlerts(counts, vault, todos, readLater));
        return result;
    }

    /** 今日必办提醒：按优先级聚合待办/回收站/密码库/稍后读，空列表表示无需处理 */
    private List<Map<String, Object>> buildAlerts(
            Map<String, Object> counts,
            Map<String, Object> vault,
            Map<String, Object> todos,
            Map<String, Object> readLater) {
        List<Map<String, Object>> alerts = new ArrayList<>();

        int dueToday = ((Number) todos.getOrDefault("dueToday", 0)).intValue();
        int overdue = ((Number) todos.getOrDefault("overdue", 0)).intValue();
        if (dueToday > 0 || overdue > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "todo");
            alert.put("level", overdue > 0 ? "danger" : "warning");
            alert.put("title", "今日待办");
            alert.put("desc", buildDesc(dueToday, "件今天到期", overdue, "件已逾期"));
            alert.put("count", dueToday + overdue);
            alert.put("action", "/todos");
            alerts.add(alert);
        }

        int recycleTotal = 0;
        Object recycle = counts.get("recycle");
        if (recycle instanceof Map<?, ?>) {
            for (Object v : ((Map<?, ?>) recycle).values()) {
                if (v instanceof Number) recycleTotal += ((Number) v).intValue();
            }
        }
        if (recycleTotal > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "recycle");
            alert.put("level", "warning");
            alert.put("title", "回收站待清理");
            alert.put("desc", "有 " + recycleTotal + " 条内容正在占用回收站");
            alert.put("count", recycleTotal);
            alert.put("action", "/recycle");
            alerts.add(alert);
        }

        int duplicate = ((Number) vault.getOrDefault("duplicateCount", 0)).intValue();
        if (duplicate > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "vault");
            alert.put("level", "danger");
            alert.put("title", "密码库健康");
            alert.put("desc", "检测到 " + duplicate + " 个重复密码，建议尽快处理");
            alert.put("count", duplicate);
            alert.put("action", "/?module=vault");
            alerts.add(alert);
        }

        int readLaterCount = ((Number) readLater.getOrDefault("count", 0)).intValue();
        if (readLaterCount > 0) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "readLater");
            alert.put("level", "info");
            alert.put("title", "继续阅读");
            alert.put("desc", "还有 " + readLaterCount + " 篇内容未读完");
            alert.put("count", readLaterCount);
            alert.put("action", "/?module=bookmarks");
            alerts.add(alert);
        }

        return alerts;
    }

    /** 拼接描述：如 “2 件今天到期，1 件已逾期”；没有前半时省略顿号 */
    private String buildDesc(int a, String aText, int b, String bText) {
        StringBuilder sb = new StringBuilder();
        if (a > 0) sb.append(a).append(aText);
        if (a > 0 && b > 0) sb.append("，");
        if (b > 0) sb.append(b).append(bText);
        return sb.toString();
    }

    /** 稍后读：未读完的稍后读收藏（progress<100），按最近阅读时间倒序取前 10 */
    private Map<String, Object> readLater(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        bookmarkMapper.selectList(new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, userId)
                        .eq(Bookmark::getDeleted, 0)
                        .eq(Bookmark::getReadLater, 1)
                        .orderByDesc(Bookmark::getReadAt)
                        .last("LIMIT 10"))
                .forEach(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("title", b.getTitle());
                    m.put("url", b.getUrl());
                    m.put("progress", b.getReadProgress() == null ? 0 : b.getReadProgress());
                    m.put("readAt", b.getReadAt());
                    list.add(m);
                });
        Map<String, Object> rl = new LinkedHashMap<>();
        rl.put("count", list.size());
        rl.put("list", list);
        return rl;
    }

    /** 待办概览：进行中 / 今日到期 / 已逾期 */
    private Map<String, Object> todoCounts(String userId) {
        LocalDate today = LocalDate.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getUserId, userId).eq(Todo::getDeleted, 0).eq(Todo::getCompleted, 0)));
        m.put("dueToday", todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getUserId, userId).eq(Todo::getDeleted, 0).eq(Todo::getCompleted, 0)
                .eq(Todo::getDueDate, today)));
        m.put("overdue", todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getUserId, userId).eq(Todo::getDeleted, 0).eq(Todo::getCompleted, 0)
                .lt(Todo::getDueDate, today)));
        return m;
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

    /**
     * 近 N 月各模块新增趋势（默认 6，上限 12）。
     * 返回按时间升序的月份列表：{ month:"yyyy-MM", bookmarks, notes, files, vault }（含全 0 月份）
     */
    public List<Map<String, Object>> trend(int months) {
        String userId = StpUtil.getLoginIdAsString();
        int n = Math.max(1, Math.min(12, months));

        YearMonth now = YearMonth.now();
        Map<String, int[]> agg = new LinkedHashMap<>(); // month -> [bookmarks, notes, files, vault]
        List<String> keys = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            String key = now.minusMonths(i).toString();
            keys.add(key);
            agg.put(key, new int[4]);
        }

        for (Bookmark b : bookmarkMapper.selectList(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId).eq(Bookmark::getDeleted, 0)
                .select(Bookmark::getCreateTime))) {
            bucket(agg, b.getCreateTime(), 0);
        }
        for (Note note : noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId).eq(Note::getDeleted, 0)
                .select(Note::getCreateTime))) {
            bucket(agg, note.getCreateTime(), 1);
        }
        for (FileRecord f : fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getUserId, userId).eq(FileRecord::getDeleted, 0)
                .select(FileRecord::getCreateTime))) {
            bucket(agg, f.getCreateTime(), 2);
        }
        for (Vault v : vaultMapper.selectList(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId).eq(Vault::getDeleted, 0)
                .select(Vault::getCreateTime))) {
            bucket(agg, v.getCreateTime(), 3);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : keys) {
            int[] c = agg.get(key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", key);
            row.put("bookmarks", c[0]);
            row.put("notes", c[1]);
            row.put("files", c[2]);
            row.put("vault", c[3]);
            result.add(row);
        }
        return result;
    }

    /** 按月份（yyyy-MM）把 createTime 计入对应桶（越界月份忽略） */
    private void bucket(Map<String, int[]> agg, LocalDateTime createTime, int idx) {
        if (createTime == null) return;
        String key = createTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        int[] c = agg.get(key);
        if (c != null) c[idx]++;
    }
}
