package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.entity.TodoItem;
import com.jnclub.bookmark.mapper.TodoItemMapper;
import com.jnclub.bookmark.mapper.TodoMapper;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办清单服务 — 手动 CRUD + 优先级/截止日期/完成状态 + 子任务 + 重复规则 + 分页
 */
@Service
@RequiredArgsConstructor
public class TodoService extends ServiceImpl<TodoMapper, Todo> {

    private final TodoItemMapper todoItemMapper;

    /**
     * 列表（按当前用户过滤）
     *
     * @param filter all=全部 / active=进行中 / completed=已完成 / today=今天应处理（未完成且截止<=今天或无截止）/
     *               overdue=已逾期未完成 / tomorrow=明天 / week=未来7天 / noDate=无日期 / high=高优先级
     */
    public List<Todo> list(String filter) {
        LambdaQueryWrapper<Todo> qw = buildWrapper(filter);
        List<Todo> todos = list(qw);
        todos.forEach(this::fillItemStats);
        return todos;
    }

    /**
     * 分页列表（保留 list 默认行为；page/pageSize 显式传入时返回 { list, page, pageSize, total }）
     */
    public Map<String, Object> page(String filter, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        LambdaQueryWrapper<Todo> qw = buildWrapper(filter);
        Long total = count(qw);
        int offset = (safePage - 1) * safeSize;
        qw.last("LIMIT " + offset + "," + safeSize);
        List<Todo> todos = list(qw);
        todos.forEach(this::fillItemStats);
        Map<String, Object> result = new HashMap<>();
        result.put("list", todos);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    private LambdaQueryWrapper<Todo> buildWrapper(String filter) {
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
            case "tomorrow" -> qw.eq(Todo::getCompleted, 0).eq(Todo::getDueDate, today.plusDays(1));
            case "week" -> qw.eq(Todo::getCompleted, 0)
                    .ge(Todo::getDueDate, today)
                    .le(Todo::getDueDate, today.plusDays(7));
            case "noDate" -> qw.isNull(Todo::getDueDate);
            case "high" -> qw.eq(Todo::getPriority, 2);
            default -> { /* all */ }
        }

        // 排序：未完成在前 → 优先级高在前 → 截止日近在前 → 手动序 → 创建新在前
        qw.orderByAsc(Todo::getCompleted)
                .orderByDesc(Todo::getPriority)
                .orderByAsc(Todo::getDueDate)
                .orderByAsc(Todo::getSortOrder)
                .orderByDesc(Todo::getCreateTime);
        return qw;
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
        if (todo.getRemindNotified() == null) todo.setRemindNotified(0);
        if (todo.getRecurrenceInterval() == null) todo.setRecurrenceInterval(1);
        if (todo.getRecurrence() != null && todo.getRecurrence().isBlank()) todo.setRecurrence(null);
        save(todo);
        return todo;
    }

    /** 编辑（null 字段忽略；显式清空截止日期/时间/提醒传 clear* = true） */
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
        if (Boolean.TRUE.equals(patch.getClearDueTime())) {
            exist.setDueTime(null);
        } else if (patch.getDueTime() != null) {
            exist.setDueTime(patch.getDueTime());
        }
        if (Boolean.TRUE.equals(patch.getClearRemindAt())) {
            exist.setRemindAt(null);
            exist.setRemindNotified(0);
        } else if (patch.getRemindAt() != null) {
            exist.setRemindAt(patch.getRemindAt());
            exist.setRemindNotified(0);
        }
        if (patch.getRecurrence() != null) {
            if (patch.getRecurrence().isBlank()) {
                exist.setRecurrence(null);
            } else {
                exist.setRecurrence(patch.getRecurrence().toUpperCase());
                exist.setRemindNotified(0);
            }
        }
        if (patch.getRecurrenceInterval() != null) {
            exist.setRecurrenceInterval(Math.max(1, patch.getRecurrenceInterval()));
        }
        updateById(exist);
    }

    /** 切换完成状态；重复待办完成后自动推进到下一次并保持进行中 */
    public void setCompleted(Long id, boolean completed) {
        Todo exist = requireOwned(id);
        if (completed && exist.getRecurrence() != null && !exist.getRecurrence().isBlank()) {
            advanceRecurring(exist);
            exist.setCompleted(0);
            exist.setCompletedAt(null);
            exist.setRemindNotified(0);
            updateById(exist);
            return;
        }
        exist.setCompleted(completed ? 1 : 0);
        exist.setCompletedAt(completed ? LocalDateTime.now() : null);
        updateById(exist);
    }

    /** 删除（物理删除，同时删子任务） */
    public void delete(Long id) {
        Todo exist = requireOwned(id);
        removeById(exist.getId());
        todoItemMapper.delete(new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getTodoId, exist.getId()));
    }

    // ======================== 子任务 ========================

    public List<TodoItem> listItems(Long todoId) {
        requireOwned(todoId);
        return todoItemMapper.selectList(new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getTodoId, todoId)
                .eq(TodoItem::getDeleted, 0)
                .orderByAsc(TodoItem::getCompleted)
                .orderByAsc(TodoItem::getSortOrder)
                .orderByAsc(TodoItem::getId));
    }

    public TodoItem addItem(Long todoId, TodoItem item) {
        requireOwned(todoId);
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new BizException("子任务标题不能为空");
        }
        item.setId(null);
        item.setTodoId(todoId);
        item.setUserId(StpUtil.getLoginIdAsString());
        item.setTitle(item.getTitle().trim());
        if (item.getCompleted() == null) item.setCompleted(0);
        if (item.getSortOrder() == null) item.setSortOrder(0);
        if (item.getDeleted() == null) item.setDeleted(0);
        todoItemMapper.insert(item);
        return item;
    }

    public void updateItem(Long todoId, Long itemId, TodoItem patch) {
        TodoItem exist = requireItemOwned(todoId, itemId);
        if (patch.getTitle() != null && !patch.getTitle().isBlank()) {
            exist.setTitle(patch.getTitle().trim());
        }
        if (patch.getCompleted() != null) exist.setCompleted(patch.getCompleted());
        if (patch.getSortOrder() != null) exist.setSortOrder(patch.getSortOrder());
        todoItemMapper.updateById(exist);
    }

    public void setItemCompleted(Long todoId, Long itemId, boolean completed) {
        TodoItem exist = requireItemOwned(todoId, itemId);
        exist.setCompleted(completed ? 1 : 0);
        todoItemMapper.updateById(exist);
    }

    public void deleteItem(Long todoId, Long itemId) {
        TodoItem exist = requireItemOwned(todoId, itemId);
        todoItemMapper.deleteById(exist.getId());
    }

    // ======================== 内部方法 ========================

    private Todo requireOwned(Long id) {
        Todo exist = getById(id);
        if (exist == null || !exist.getUserId().equals(StpUtil.getLoginIdAsString())) {
            throw new BizException("待办不存在或无权操作");
        }
        return exist;
    }

    private TodoItem requireItemOwned(Long todoId, Long itemId) {
        TodoItem exist = todoItemMapper.selectById(itemId);
        String userId = StpUtil.getLoginIdAsString();
        if (exist == null || !exist.getTodoId().equals(todoId) || !exist.getUserId().equals(userId)) {
            throw new BizException("子任务不存在或无权操作");
        }
        return exist;
    }

    private void fillItemStats(Todo todo) {
        Long total = todoItemMapper.selectCount(new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getTodoId, todo.getId())
                .eq(TodoItem::getDeleted, 0));
        Long done = todoItemMapper.selectCount(new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getTodoId, todo.getId())
                .eq(TodoItem::getDeleted, 0)
                .eq(TodoItem::getCompleted, 1));
        todo.setItemCount(total == null ? 0 : total.intValue());
        todo.setItemCompletedCount(done == null ? 0 : done.intValue());
    }

    /** 推进重复待办到下一次 */
    private void advanceRecurring(Todo todo) {
        LocalDate due = todo.getDueDate() == null ? LocalDate.now() : todo.getDueDate();
        int interval = todo.getRecurrenceInterval() == null ? 1 : Math.max(1, todo.getRecurrenceInterval());
        LocalDate next = switch (todo.getRecurrence().toUpperCase()) {
            case "DAILY" -> due.plusDays(interval);
            case "WEEKLY" -> due.plusWeeks(interval);
            case "MONTHLY" -> due.plusMonths(interval);
            case "YEARLY" -> due.plusYears(interval);
            default -> due.plusDays(interval);
        };
        todo.setDueDate(next);
        if (todo.getRemindAt() != null) {
            LocalDateTime base = todo.getRemindAt();
            LocalDateTime advanced = switch (todo.getRecurrence().toUpperCase()) {
                case "DAILY" -> base.plusDays(interval);
                case "WEEKLY" -> base.plusWeeks(interval);
                case "MONTHLY" -> base.plusMonths(interval);
                case "YEARLY" -> base.plusYears(interval);
                default -> base.plusDays(interval);
            };
            todo.setRemindAt(advanced);
        }
    }
}