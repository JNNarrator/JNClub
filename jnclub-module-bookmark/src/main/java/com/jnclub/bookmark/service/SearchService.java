package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Tag;
import com.jnclub.bookmark.entity.TagRelation;
import com.jnclub.bookmark.entity.Vault;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.TagMapper;
import com.jnclub.bookmark.mapper.TagRelationMapper;
import com.jnclub.bookmark.mapper.VaultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索服务 — 收藏 / 便签 / 云盘 / 密码库(仅标题) / 标签 / 音乐曲目 跨模块聚合
 * 多关键词按空格拆分做 AND 匹配；返回匹配高亮区间 [{field, ranges:[[s,e]]}]，前端渲染高亮
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final BookmarkMapper bookmarkMapper;
    private final NoteMapper noteMapper;
    private final FileMapper fileMapper;
    private final VaultMapper vaultMapper;
    private final TagMapper tagMapper;
    private final TagRelationMapper tagRelationMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 跨模块搜索。返回 {bookmarks, notes, files, vault, tags, tracks}；note 附摘要 excerpt。
     */
    public Map<String, Object> search(String keyword, int limit) {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookmarks", List.of());
        result.put("notes", List.of());
        result.put("files", List.of());
        result.put("vault", List.of());
        result.put("tags", List.of());
        result.put("tracks", List.of());

        if (keyword == null || keyword.isBlank()) {
            return result;
        }
        List<String> terms = Arrays.stream(keyword.trim().toLowerCase().split("\\s+"))
                .filter(t -> !t.isBlank())
                .toList();
        if (terms.isEmpty()) return result;

        int size = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 50));

        // 收藏：标题 + URL（每个关键词须命中其一）
        List<Bookmark> bookmarks = bookmarkMapper.selectPage(new Page<>(1, size),
                        new LambdaQueryWrapper<Bookmark>()
                                .eq(Bookmark::getUserId, userId)
                                .eq(Bookmark::getDeleted, 0)
                                .and(w -> {
                                    for (String t : terms) w.and(x -> x.like(Bookmark::getTitle, t).or().like(Bookmark::getUrl, t));
                                })
                                .orderByDesc(Bookmark::getCreateTime))
                .getRecords();
        List<Map<String, Object>> bookmarkResults = bookmarks.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("title", b.getTitle());
            m.put("url", b.getUrl());
            m.put("icon", b.getIcon());
            m.put("directoryId", b.getDirectoryId());
            m.put("highlights", buildHighlights(Map.of("title", b.getTitle(), "url", b.getUrl()), terms));
            return m;
        }).toList();

        // 便签：标题 + Markdown 内容（返回轻量对象 + 摘要，避免大内容体）
        List<Note> notes = noteMapper.selectPage(new Page<>(1, size),
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getUserId, userId)
                                .eq(Note::getDeleted, 0)
                                .and(w -> {
                                    for (String t : terms) w.and(x -> x.like(Note::getTitle, t).or().like(Note::getContent, t));
                                })
                                .orderByDesc(Note::getCreateTime))
                .getRecords();
        List<Map<String, Object>> noteResults = notes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("directoryId", n.getDirectoryId());
            m.put("createTime", n.getCreateTime());
            m.put("excerpt", excerpt(n.getContent(), 120));
            m.put("highlights", buildHighlights(Map.of("title", n.getTitle(), "content", n.getContent()), terms));
            return m;
        }).toList();

        // 云盘：文件名
        List<FileRecord> files = fileMapper.selectPage(new Page<>(1, size),
                        new LambdaQueryWrapper<FileRecord>()
                                .eq(FileRecord::getUserId, userId)
                                .eq(FileRecord::getDeleted, 0)
                                .and(w -> {
                                    for (String t : terms) w.like(FileRecord::getOriginalName, t);
                                })
                                .orderByDesc(FileRecord::getCreateTime))
                .getRecords();
        List<Map<String, Object>> fileResults = files.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("originalName", f.getOriginalName());
            m.put("directoryId", f.getDirectoryId());
            m.put("size", f.getSize());
            m.put("highlights", buildHighlights(Map.of("originalName", f.getOriginalName()), terms));
            return m;
        }).toList();

        // 密码库：名称 + 账号 + 站点 + 备注（密文密码不触碰，安全边界）
        List<Vault> vaults = vaultMapper.selectList(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId)
                .eq(Vault::getDeleted, 0)
                .and(w -> {
                    for (String t : terms) w.and(x -> x.like(Vault::getName, t).or().like(Vault::getUsername, t)
                            .or().like(Vault::getUrl, t).or().like(Vault::getNotes, t));
                })
                .orderByDesc(Vault::getCreateTime)
                .last("LIMIT " + size));
        List<Map<String, Object>> vaultResults = vaults.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("name", v.getName());
            m.put("username", v.getUsername());
            m.put("url", v.getUrl());
            m.put("directoryId", v.getDirectoryId());
            m.put("highlights", buildHighlights(Map.of(
                    "name", v.getName(),
                    "username", v.getUsername(),
                    "url", v.getUrl(),
                    "notes", v.getNotes()), terms));
            return m;
        }).toList();

        // 标签：按名称匹配，附关联对象数
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .and(w -> {
                    for (String t : terms) w.like(Tag::getName, t);
                })
                .orderByDesc(Tag::getCreateTime)
                .last("LIMIT " + size));
        List<Map<String, Object>> tagResults = tags.stream().map(tg -> {
            Long cnt = tagRelationMapper.selectCount(new LambdaQueryWrapper<TagRelation>()
                    .eq(TagRelation::getTagId, tg.getId()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tg.getId());
            m.put("name", tg.getName());
            m.put("count", cnt == null ? 0 : cnt);
            m.put("highlights", buildHighlights(Map.of("name", tg.getName()), terms));
            return m;
        }).toList();

        // 音乐曲目：music_track 表只读（同库，不依赖蓝奏云在线状态）
        List<Map<String, Object>> trackResults = searchTracks(terms, size);

        result.put("bookmarks", bookmarkResults);
        result.put("notes", noteResults);
        result.put("files", fileResults);
        result.put("vault", vaultResults);
        result.put("tags", tagResults);
        result.put("tracks", trackResults);
        return result;
    }

    /** 音乐曲目搜索（JdbcTemplate 直查 music_track，标题/歌手） */
    private List<Map<String, Object>> searchTracks(List<String> terms, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT track_id, name, artist, album, duration, has_lyric FROM music_track WHERE 1=1");
        List<Object> args = new ArrayList<>();
        for (String t : terms) {
            sql.append(" AND (name LIKE ? OR artist LIKE ?)");
            args.add("%" + t + "%");
            args.add("%" + t + "%");
        }
        sql.append(" ORDER BY name LIMIT ?");
        args.add(size);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("trackId", row.get("track_id"));
                m.put("name", row.get("name"));
                m.put("artist", row.get("artist"));
                m.put("album", row.get("album"));
                m.put("duration", row.get("duration"));
                m.put("hasLyric", row.get("has_lyric"));
                m.put("highlights", buildHighlights(Map.of(
                        "name", String.valueOf(row.get("name")),
                        "artist", String.valueOf(row.get("artist"))), terms));
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            // music_track 表不存在（音乐模块未初始化）时静默返回空
            return List.of();
        }
    }

    /** 按字段构建高亮区间：{field: [[start,end],...]}（区间已合并重叠） */
    private List<Map<String, Object>> buildHighlights(Map<String, String> fields, List<String> terms) {
        List<Map<String, Object>> highlights = new ArrayList<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            List<int[]> spans = findMatches(e.getValue(), terms);
            if (spans.isEmpty()) continue;
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("field", e.getKey());
            h.put("ranges", spans);
            highlights.add(h);
        }
        return highlights;
    }

    /** 在文本中查找所有关键词出现区间（大小写不敏感），合并重叠 */
    private List<int[]> findMatches(String text, List<String> terms) {
        if (text == null || text.isEmpty()) return List.of();
        String lower = text.toLowerCase();
        List<int[]> spans = new ArrayList<>();
        for (String t : terms) {
            int from = 0;
            while ((from = lower.indexOf(t, from)) >= 0) {
                spans.add(new int[]{from, from + t.length()});
                from += t.length();
            }
        }
        if (spans.isEmpty()) return List.of();
        spans.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] sp : spans) {
            if (!merged.isEmpty() && sp[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], sp[1]);
            } else {
                merged.add(new int[]{sp[0], sp[1]});
            }
        }
        return merged;
    }

    /**
     * Markdown 摘要：去标记符号 → 压缩空白 → 截断
     */
    private String excerpt(String content, int maxLen) {
        if (content == null || content.isBlank()) return "";
        String plain = content
                .replaceAll("[#*`\\[\\]()!>_~\\-]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.length() > maxLen) {
            return plain.substring(0, maxLen) + "…";
        }
        return plain;
    }
}
