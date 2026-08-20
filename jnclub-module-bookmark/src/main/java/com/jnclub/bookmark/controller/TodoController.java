package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Todo;
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

    /** 待办列表：filter = all|active|completed|today|overdue */
    @GetMapping
    public R<List<Todo>> list(@RequestParam(defaultValue = "all") String filter) {
        return R.ok(todoService.list(filter));
    }

    /** 新建待办 */
    @PostMapping
    public R<Todo> create(@RequestBody Todo todo) {
        return R.ok(todoService.create(todo));
    }

    /** 编辑待办（标题/备注/优先级/截止日期） */
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
}
