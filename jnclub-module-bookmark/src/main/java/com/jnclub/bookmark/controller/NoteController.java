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
     * 便签列表（archived=true 时返回全部已归档便签，跨目录）
     */
    @GetMapping
    public R<List<Note>> getNotes(@RequestParam Long directoryId,
                                  @RequestParam(required = false) Long tagId,
                                  @RequestParam(defaultValue = "false") boolean archived) {
        return R.ok(noteService.getNotes(directoryId, tagId, archived));
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

    /** 置顶/取消置顶：body { pinned } */
    @PutMapping("/{id}/pin")
    public R<Void> setPinned(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean pinned = body.get("pinned") != null && Boolean.parseBoolean(String.valueOf(body.get("pinned")));
        noteService.setPinned(id, pinned);
        return R.ok();
    }

    /** 归档/取消归档：body { archived } */
    @PutMapping("/{id}/archive")
    public R<Void> setArchived(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean archived = body.get("archived") != null && Boolean.parseBoolean(String.valueOf(body.get("archived")));
        noteService.setArchived(id, archived);
        return R.ok();
    }

    /** 历史版本列表 */
    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> listVersions(@PathVariable Long id) {
        return R.ok(noteService.listVersions(id));
    }

    /** 历史版本详情（含完整内容） */
    @GetMapping("/{id}/versions/{versionId}")
    public R<Object> getVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return R.ok(noteService.getVersion(id, versionId));
    }

    /** 回滚到指定版本 */
    @PutMapping("/{id}/restore-version")
    public R<Void> restoreVersion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long versionId = body.get("versionId") == null
                ? null : Long.parseLong(String.valueOf(body.get("versionId")));
        if (versionId == null) throw new RuntimeException("缺少 versionId");
        noteService.restoreVersion(id, versionId);
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
