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
     * 获取目录下的便签列表（tagId 可选：按标签筛选）
     */
    @GetMapping
    public R<List<Note>> getNotes(@RequestParam Long directoryId,
                                  @RequestParam(required = false) Long tagId) {
        return R.ok(noteService.getNotes(directoryId, tagId));
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
     * 移动便签到其他目录：body { directoryId }
     */
    @PutMapping("/{id}/move")
    public R<Void> moveNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        noteService.moveNote(id, directoryId);
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

    /** 批量移动：body { ids[], directoryId } */
    @PutMapping("/batch-move")
    public R<Void> batchMove(@RequestBody Map<String, Object> body) {
        List<Long> ids = parseIds(body.get("ids"));
        Long directoryId = body.get("directoryId") == null
                ? null : Long.parseLong(String.valueOf(body.get("directoryId")));
        noteService.moveNotesBatch(ids, directoryId);
        return R.ok();
    }

    /** 批量删除（软删除进回收站）：body { ids[] } */
    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody Map<String, Object> body) {
        noteService.deleteNotesBatch(parseIds(body.get("ids")));
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseIds(Object raw) {
        if (raw == null) return List.of();
        return ((List<Object>) raw).stream().map(v -> Long.parseLong(String.valueOf(v))).toList();
    }
}
