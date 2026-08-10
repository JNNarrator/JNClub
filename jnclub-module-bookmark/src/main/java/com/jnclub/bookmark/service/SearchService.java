package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索服务 — 收藏（标题+URL）/ 便签（标题+内容）/ 云盘（文件名）跨模块聚合
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final BookmarkMapper bookmarkMapper;
    private final NoteMapper noteMapper;
    private final FileMapper fileMapper;

    /**
     * 跨模块搜索。返回 {bookmarks, notes, files}；note 附摘要 excerpt。
     */
    public Map<String, Object> search(String keyword, int limit) {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookmarks", List.of());
        result.put("notes", List.of());
        result.put("files", List.of());

        if (keyword == null || keyword.isBlank()) {
            return result;
        }
        String kw = keyword.trim();
        int size = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 50));

        // 收藏：标题 + URL
        List<Bookmark> bookmarks = bookmarkMapper.selectPage(new Page<>(1, size),
                        new LambdaQueryWrapper<Bookmark>()
                                .eq(Bookmark::getUserId, userId)
                                .eq(Bookmark::getDeleted, 0)
                                .and(w -> w.like(Bookmark::getTitle, kw)
                                        .or().like(Bookmark::getUrl, kw))
                                .orderByDesc(Bookmark::getCreateTime))
                .getRecords();

        // 便签：标题 + Markdown 内容（返回轻量对象 + 摘要，避免大内容体）
        List<Note> notes = noteMapper.selectPage(new Page<>(1, size),
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getUserId, userId)
                                .eq(Note::getDeleted, 0)
                                .and(w -> w.like(Note::getTitle, kw)
                                        .or().like(Note::getContent, kw))
                                .orderByDesc(Note::getCreateTime))
                .getRecords();
        List<Map<String, Object>> noteResults = notes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("directoryId", n.getDirectoryId());
            m.put("createTime", n.getCreateTime());
            m.put("excerpt", excerpt(n.getContent(), 120));
            return m;
        }).toList();

        // 云盘：文件名
        List<FileRecord> files = fileMapper.selectPage(new Page<>(1, size),
                        new LambdaQueryWrapper<FileRecord>()
                                .eq(FileRecord::getUserId, userId)
                                .eq(FileRecord::getDeleted, 0)
                                .like(FileRecord::getOriginalName, kw)
                                .orderByDesc(FileRecord::getCreateTime))
                .getRecords();

        result.put("bookmarks", bookmarks);
        result.put("notes", noteResults);
        result.put("files", files);
        return result;
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
