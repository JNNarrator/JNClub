package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.service.NoteService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 便签控制器
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * 获取目录下的便签列表
     */
    @GetMapping
    public R<List<Note>> getNotes(@RequestParam Long directoryId) {
        return R.ok(noteService.getNotes(directoryId));
    }

    /**
     * 获取便签详情
     */
    @GetMapping("/{id}")
    public R<Note> getNoteDetail(@PathVariable Long id) {
        return R.ok(noteService.getNoteDetail(id));
    }

    /**
     * 新建便签
     */
    @PostMapping
    public R<Note> createNote(@RequestBody Note note) {
        return R.ok(noteService.createNote(note));
    }

    /**
     * 编辑便签
     */
    @PutMapping("/{id}")
    public R<Void> updateNote(@PathVariable Long id, @RequestBody Note note) {
        noteService.updateNote(id, note);
        return R.ok();
    }

    /**
     * 删除便签
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
        return R.ok();
    }

    /**
     * 批量更新排序
     */
    @PutMapping("/sort")
    public R<Void> updateSortOrder(@RequestBody List<Map<String, Object>> sortList) {
        noteService.updateSortOrder(sortList);
        return R.ok();
    }
}
