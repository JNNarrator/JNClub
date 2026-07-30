package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.mapper.NoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 便签服务 — 标题自动派生 + 批量排序
 */
@Service
public class NoteService extends ServiceImpl<NoteMapper, Note> {

    /**
     * 获取目录下的便签列表
     */
    public List<Note> getNotes(Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        List<Note> notes = list(new LambdaQueryWrapper<Note>()
                .eq(Note::getDirectoryId, directoryId)
                .eq(Note::getUserId, userId)
                .orderByAsc(Note::getSortOrder));

        // 为每个便签派生显示标题
        for (Note note : notes) {
            note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        }
        return notes;
    }

    /**
     * 获取便签详情
     */
    public Note getNoteDetail(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        // 派生显示标题
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        return note;
    }

    /**
     * 新建便签
     */
    public Note createNote(Note note) {
        String userId = StpUtil.getLoginIdAsString();
        note.setUserId(userId);
        // 标题派生
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        save(note);
        return note;
    }

    /**
     * 编辑便签
     */
    public void updateNote(Long id, Note note) {
        String userId = StpUtil.getLoginIdAsString();
        Note existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }

        existing.setTitle(note.getTitle());
        existing.setContent(note.getContent());
        // 标题派生
        existing.setTitle(deriveDisplayTitle(existing.getTitle(), existing.getContent()));

        updateById(existing);
    }

    /**
     * 删除便签
     */
    public void deleteNote(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        removeById(id);
    }

    /**
     * 批量更新排序
     */
    @Transactional
    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = StpUtil.getLoginIdAsString();
        List<Note> toUpdate = new ArrayList<>();
        for (Map<String, Object> item : sortList) {
            Long id = Long.parseLong(item.get("id").toString());
            Integer sortOrder = Integer.parseInt(item.get("sortOrder").toString());

            Note note = getById(id);
            if (note != null && note.getUserId().equals(userId)) {
                note.setSortOrder(sortOrder);
                toUpdate.add(note);
            }
        }
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
        }
    }

    /**
     * 标题派生规则：
     * 1. 若 title 非空且非纯空白 → 直接返回
     * 2. 若 content 非空 → 取首行 # 标题或前 30 字
     * 3. 否则 → "无标题"
     */
    private String deriveDisplayTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (content != null && !content.isBlank()) {
            // 尝试取首行 # 标题
            String[] lines = content.strip().split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
                if (trimmed.startsWith("## ")) {
                    return trimmed.substring(3).trim();
                }
            }
            // 取前 30 字
            String plain = content.replaceAll("[#*`\\[\\]()!>_~\\-]", "").trim();
            if (plain.length() > 30) {
                return plain.substring(0, 30) + "…";
            }
            return plain;
        }
        return "无标题";
    }
}
