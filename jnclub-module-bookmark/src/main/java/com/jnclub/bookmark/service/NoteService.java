package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.mapper.NoteMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 便签服务
 */
@Service
public class NoteService extends ServiceImpl<NoteMapper, Note> {

    /**
     * 获取目录下的便签列表
     */
    public List<Note> getNotes(Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        return list(new LambdaQueryWrapper<Note>()
                .eq(Note::getDirectoryId, directoryId)
                .eq(Note::getUserId, userId)
                .orderByAsc(Note::getSortOrder));
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
        return note;
    }

    /**
     * 新建便签
     */
    public Note createNote(Note note) {
        String userId = StpUtil.getLoginIdAsString();
        note.setUserId(userId);
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
    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = StpUtil.getLoginIdAsString();
        for (Map<String, Object> item : sortList) {
            Long id = Long.parseLong(item.get("id").toString());
            Integer sortOrder = Integer.parseInt(item.get("sortOrder").toString());
            
            Note note = getById(id);
            if (note != null && note.getUserId().equals(userId)) {
                note.setSortOrder(sortOrder);
                updateById(note);
            }
        }
    }
}
