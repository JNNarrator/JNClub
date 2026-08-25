package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Tag;
import com.jnclub.bookmark.service.TagService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 标签控制器 — 标签 CRUD + 关联设置
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 我的标签列表（refType 可选：bookmark/note，带 count）
     */
    @GetMapping
    public R<List<Tag>> listTags(@RequestParam(required = false) String refType) {
        return R.ok(tagService.listTags(refType));
    }

    /**
     * 新建标签（同名幂等）
     */
    @PostMapping
    public R<Tag> createTag(@RequestBody Map<String, String> body) {
        return R.ok(tagService.createTag(body.get("name")));
    }

    /**
     * 重命名标签
     */
    @PutMapping("/{id}")
    public R<Void> renameTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        tagService.renameTag(id, body.get("name"));
        return R.ok();
    }

    /**
     * 删除标签（级联清空关联）
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return R.ok();
    }

    /**
     * 查询单条记录关联的标签（带标签名）
     */
    @GetMapping("/relations")
    public R<List<Tag>> listTagsOfRef(@RequestParam String refType,
                                      @RequestParam Long refId) {
        return R.ok(tagService.listTagsOfRef(refType, refId));
    }

    /**
     * 批量查询多条记录关联的标签（带标签名），返回 { refId: Tag[] }
     * 列表页使用一次请求替代 N+1。
     */
    @GetMapping("/relations/batch")
    public R<Map<Long, List<Tag>>> listTagsOfRefs(@RequestParam String refType,
                                                  @RequestParam List<Long> refIds) {
        return R.ok(tagService.listTagsOfRefs(refType, refIds));
    }

    /**
     * 全量覆盖设置关联：body = {refType, refId, tagNames[]}
     */
    @PutMapping("/relations")
    public R<Void> setRelations(@RequestBody Map<String, Object> body) {
        String refType = (String) body.get("refType");
        Long refId = body.get("refId") == null
                ? null : Long.parseLong(String.valueOf(body.get("refId")));
        @SuppressWarnings("unchecked")
        List<String> tagNames = (List<String>) body.get("tagNames");
        tagService.setRelations(refType, refId, tagNames);
        return R.ok();
    }
}
