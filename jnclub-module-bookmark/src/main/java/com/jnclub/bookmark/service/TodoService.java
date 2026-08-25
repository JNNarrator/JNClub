package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.mapper.TodoMapper;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办清单服务 — 手动 CRUD + 优先级/截止日期/完成状态
 */
@Service
@RequiredArgsConstructor
public class TodoService extends ServiceImpl<TodoMapper, Todo> {

    /**
     * 列表（按当前用户过滤）
     *
     * @param filter all=全部 / active=进行中 / completed=已完成 / today=今天应处理（未完成且截止<=今天或无截止）/ overdue=已逾期未完成
     */
    public List<Todo> list(String filter) {
        String userId = StpUtil.getLoginIdAsString();
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Todo> qw = new LambdaQueryWrapper<Todo>()
                .eq(Todo::getUserId, userId)
                .eq(Todo::getDeleted, 0);

        String f = filter == null ? "all" : filter;
        switch (f) {
            case "active" -> qw.eq(Todo::getCompleted, 0);
            case "completed" -> qw.eq(Todo::getCompleted, 1);
            case "overdue" -> qw.eq(Todo::getCompleted, 0).lt(Todo::getDueDate, today);
            case "today" -> qw.eq(Todo::getCompleted, 0)
                    .and(w -> w.le(Todo::getDueDate, today).or().isNull(Todo::getDueDate));
            default -> { /* all */ }
        }

        // 排序：未完成在前 → 优先级高在前 → 截止日近在前 → 手动序 → 创建新在前
        qw.orderByAsc(Todo::getCompleted)
                .orderByDesc(Todo::getPriority)
                .orderByAsc(Todo::getDueDate)
                .orderByAsc(Todo::getSortOrder)
                .orderByDesc(Todo::getCreateTime);
        return list(qw);
    }

    /** 创建待办 */
    public Todo create(Todo todo) {
        String userId = StpUtil.getLoginIdAsString();
        if (todo.getTitle() == null || todo.getTitle().isBlank()) {
            throw new BizException("待办标题不能为空");
        }
        todo.setId(null);
        todo.setUserId(userId);
        todo.setTitle(todo.getTitle().trim());
        if (todo.getPriority() == null) todo.setPriority(0);
        if (todo.getCompleted() == null) todo.setCompleted(0);
        if (todo.getSortOrder() == null) todo.setSortOrder(0);
        if (todo.getDeleted() == null) todo.setDeleted(0);
        save(todo);
        return todo;
    }

    /** 编辑（标题/备注/优先级/截止日期；null 字段忽略，显式清空截止日期传 dueDate=null 且 clearDueDate=true） */
    public void update(Long id, Todo patch) {
        Todo exist = requireOwned(id);
        if (patch.getTitle() != null && !patch.getTitle().isBlank()) {
            exist.setTitle(patch.getTitle().trim());
        }
        if (patch.getNote() != null) exist.setNote(patch.getNote());
        if (patch.getPriority() != null) exist.setPriority(patch.getPriority());
        if (Boolean.TRUE.equals(patch.getClearDueDate())) {
            exist.setDueDate(null);
        } else if (patch.getDueDate() != null) {
            exist.setDueDate(patch.getDueDate());
        }
        updateById(exist);
    }

    /** 切换完成状态 */
    public void setCompleted(Long id, boolean completed) {
        Todo exist = requireOwned(id);
        exist.setCompleted(completed ? 1 : 0);
        exist.setCompletedAt(completed ? LocalDateTime.now() : null);
        updateById(exist);
    }

    /** 删除（物理删除） */
    public void delete(Long id) {
        Todo exist = requireOwned(id);
        removeById(exist.getId());
    }

    private Todo requireOwned(Long id) {
        Todo exist = getById(id);
        if (exist == null || !exist.getUserId().equals(StpUtil.getLoginIdAsString())) {
            throw new BizException("待办不存在或无权操作");
        }
        return exist;
    }
}
