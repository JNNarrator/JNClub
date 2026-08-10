package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 便签服务 — 标题自动派生 + 图片认领 + 批量排序
 */
@Service
public class NoteService extends ServiceImpl<NoteMapper, Note> {

    @Autowired
    private AssetCleanService assetCleanService;

    @Autowired
    private TagService tagService;

    public List<Note> getNotes(Long directoryId, Long tagId) {
        String userId = StpUtil.getLoginIdAsString();
        List<Note> notes;
        if (tagId != null) {
            List<Long> refIds = tagService.listRefIdsByTag("note", tagId, userId);
            if (refIds.isEmpty()) return List.of();
            notes = list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getDirectoryId, directoryId)
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .in(Note::getId, refIds)
                    .orderByAsc(Note::getSortOrder));
        } else {
            notes = list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getDirectoryId, directoryId)
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .orderByAsc(Note::getSortOrder));
        }

        for (Note note : notes) {
            note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        }
        return notes;
    }

    public Note getNoteDetail(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        return note;
    }

    @Transactional
    public Note createNote(Note note) {
        String userId = StpUtil.getLoginIdAsString();
        note.setUserId(userId);
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        save(note);

        assetCleanService.claimAssets(note.getId(), note.getContent());
        return note;
    }

    @Transactional
    public void updateNote(Long id, Note note) {
        String userId = StpUtil.getLoginIdAsString();
        Note existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }

        existing.setTitle(note.getTitle());
        existing.setContent(note.getContent());
        existing.setTitle(deriveDisplayTitle(existing.getTitle(), existing.getContent()));

        updateById(existing);

        assetCleanService.claimAssets(existing.getId(), existing.getContent());
    }

    @Transactional
    public void deleteNote(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        // 软删除：进入回收站。内容仍引用图片，故不 unclaim（恢复后图片依旧可用）
        note.setDeleted(1);
        updateById(note);
    }

    /**
     * 永久删除便签（回收站清空/到期清理用）：物理删记录 + 解绑图片 + 级联删标签关联
     */
    @Transactional
    public void permanentlyDeleteNote(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        removeById(id);
        assetCleanService.unclaimAssets(id);
        tagService.deleteRelationsByRef("note", id, userId);
    }

    /** 从回收站恢复便签 */
    public void restoreNote(Long id, String userId) {
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        if (note.getDeleted() == null || note.getDeleted() != 1) {
            throw new RuntimeException("便签不在回收站中");
        }
        note.setDeleted(0);
        updateById(note);
    }

    /** 无鉴权永久删除（回收站定时清理用，跳过登录态校验） */
    @Transactional
    public void purgeByIdNoAuth(Long id) {
        Note note = getById(id);
        if (note == null) return;
        removeById(id);
        assetCleanService.unclaimAssets(id);
        tagService.deleteRelationsByRef("note", id, note.getUserId());
    }

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

    private String deriveDisplayTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (content != null && !content.isBlank()) {
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
            String plain = content.replaceAll("[#*`\\[\\]()!>_~\\-]", "").trim();
            if (plain.length() > 30) {
                return plain.substring(0, 30) + "…";
            }
            return plain;
        }
        return "无标题";
    }
}
