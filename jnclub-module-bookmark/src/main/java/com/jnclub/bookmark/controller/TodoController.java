package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.entity.TodoItem;
import com.jnclub.bookmark.service.TodoService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 待办清单控制器
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /**
     * 待办列表：filter = all|active|completed|today|overdue|tomorrow|week|noDate|high
     * 不传 page/pageSize 时保持原有返回 List<Todo>；传了则返回 { list, page, pageSize, total }
     */
    @GetMapping
    public R<?> list(@RequestParam(defaultValue = "all") String filter,
                     @RequestParam(required = false) Integer page,
                     @RequestParam(required = false) Integer pageSize) {
        if (page == null && pageSize == null) {
            return R.ok(todoService.list(filter));
        }
        int p = page == null ? 1 : page;
        int s = pageSize == null ? 50 : pageSize;
        return R.ok(todoService.page(filter, p, s));
    }

    /** 新建待办 */
    @PostMapping
    public R<Todo> create(@RequestBody Todo todo) {
        return R.ok(todoService.create(todo));
    }

    /** 编辑待办（标题/备注/优先级/截止日期/截止时间/提醒/重复） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Todo todo) {
        todoService.update(id, todo);
        return R.ok();
    }

    /** 切换完成状态：{ completed: true|false } */
    @PutMapping("/{id}/complete")
    public R<Void> setCompleted(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean completed = Boolean.TRUE.equals(body.get("completed"))
                || Integer.valueOf(1).equals(body.get("completed"));
        todoService.setCompleted(id, completed);
        return R.ok();
    }

    /** 删除待办（物理删除） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return R.ok();
    }

    // ======================== 子任务 ========================

    /** 子任务列表 */
    @GetMapping("/{id}/items")
    public R<List<TodoItem>> listItems(@PathVariable Long id) {
        return R.ok(todoService.listItems(id));
    }

    /** 新增子任务 */
    @PostMapping("/{id}/items")
    public R<TodoItem> addItem(@PathVariable Long id, @RequestBody TodoItem item) {
        return R.ok(todoService.addItem(id, item));
    }

    /** 编辑子任务（标题/完成/排序） */
    @PutMapping("/{id}/items/{itemId}")
    public R<Void> updateItem(@PathVariable Long id, @PathVariable Long itemId, @RequestBody TodoItem item) {
        todoService.updateItem(id, itemId, item);
        return R.ok();
    }

    /** 切换子任务完成状态：{ completed: true|false } */
    @PutMapping("/{id}/items/{itemId}/complete")
    public R<Void> setItemCompleted(@PathVariable Long id, @PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        boolean completed = Boolean.TRUE.equals(body.get("completed"))
                || Integer.valueOf(1).equals(body.get("completed"));
        todoService.setItemCompleted(id, itemId, completed);
        return R.ok();
    }

    /** 删除子任务 */
    @DeleteMapping("/{id}/items/{itemId}")
    public R<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        todoService.deleteItem(id, itemId);
        return R.ok();
    }
}