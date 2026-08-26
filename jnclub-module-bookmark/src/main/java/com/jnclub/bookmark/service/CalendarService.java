package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.entity.TodoItem;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.TodoItemMapper;
import com.jnclub.bookmark.mapper.TodoMapper;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日历服务 — 月/周/任意范围聚合：待办（普通 + 重复动态展开）+ 便签
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final TodoMapper todoMapper;
    private final NoteMapper noteMapper;
    private final TodoItemMapper todoItemMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 某月聚合数据（兼容旧接口，内部调用 range） */
    public Map<String, Object> month(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return range(ym.atDay(1), ym.atEndOfMonth());
    }

    /**
     * 任意日期范围聚合。
     * - todos：区间内普通待办 + 重复待办动态实例
     * - overdueTodos：区间之前未完成且未归入实例的待办
     * - notes：updateTime 落在区间且未归档的便签
     * - days：按天分桶，方便周/月视图直接渲染
     */
    public Map<String, Object> range(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BizException("start 和 end 不能为空");
        }
        if (start.isAfter(end)) {
            throw new BizException("start 不能晚于 end");
        }
        String userId = StpUtil.getLoginIdAsString();

        List<Todo> todos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getUserId, userId)
                .eq(Todo::getDeleted, 0)
                .isNotNull(Todo::getDueDate)
                .and(w -> w
                        .between(Todo::getDueDate, start, end)
                        .or(o -> o
                                .isNotNull(Todo::getRecurrence)
                                .ne(Todo::getRecurrence, "")
                                .le(Todo::getDueDate, end)))
                .orderByAsc(Todo::getDueDate)
                .orderByDesc(Todo::getPriority));

        List<Map<String, Object>> overdueTodos = new ArrayList<>();
        List<Map<String, Object>> dayMaps = new ArrayList<>();
        Map<String, List<Map<String, Object>>> todosByDay = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> notesByDay = new LinkedHashMap<>();

        // 初始化 days 与分桶
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.format(DATE_FMT);
            todosByDay.put(key, new ArrayList<>());
            notesByDay.put(key, new ArrayList<>());
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", key);
            day.put("todos", todosByDay.get(key));
            day.put("notes", notesByDay.get(key));
            dayMaps.add(day);
        }

        // 未完成且区间之前：跨区间逾期（重复待办优先展开到区间内）
        List<Todo> preRange = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getUserId, userId)
                .eq(Todo::getDeleted, 0)
                .eq(Todo::getCompleted, 0)
                .isNotNull(Todo::getDueDate)
                .lt(Todo::getDueDate, start)
                .orderByAsc(Todo::getDueDate));
        for (Todo t : preRange) {
            if (t.getRecurrence() != null && !t.getRecurrence().isBlank()) {
                boolean expanded = addOccurrences(t, start, end, todosByDay);
                if (!expanded && t.getDueDate().isBefore(start)) {
                    overdueTodos.add(todoView(t));
                }
            } else {
                overdueTodos.add(todoView(t));
            }
        }

        for (Todo t : todos) {
            if (t.getRecurrence() != null && !t.getRecurrence().isBlank()) {
                addOccurrences(t, start, end, todosByDay);
            } else {
                String key = t.getDueDate().format(DATE_FMT);
                if (todosByDay.containsKey(key)) {
                    todosByDay.get(key).add(todoView(t));
                }
            }
        }

        // 便签：区间内更新且未归档
        List<Note> notes = noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getDeleted, 0)
                .eq(Note::getArchived, 0)
                .ge(Note::getUpdateTime, start.atStartOfDay())
                .lt(Note::getUpdateTime, end.plusDays(1).atStartOfDay())
                .orderByDesc(Note::getUpdateTime));
        List<Map<String, Object>> noteResults = new ArrayList<>();
        for (Note n : notes) {
            LocalDate d = n.getUpdateTime().toLocalDate();
            String key = d.format(DATE_FMT);
            if (notesByDay.containsKey(key)) {
                notesByDay.get(key).add(noteView(n));
            }
            noteResults.add(noteView(n));
        }

        // 展平 todos（保持旧接口结构）
        List<Map<String, Object>> flatTodos = new ArrayList<>();
        for (List<Map<String, Object>> list : todosByDay.values()) {
            flatTodos.addAll(list);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("start", start.format(DATE_FMT));
        result.put("end", end.format(DATE_FMT));
        result.put("todos", flatTodos);
        result.put("overdueTodos", overdueTodos);
        result.put("notes", noteResults);
        result.put("days", dayMaps);
        return result;
    }

    /** 将重复待办展开到区间内；返回是否产生了至少一个实例 */
    private boolean addOccurrences(Todo todo, LocalDate start, LocalDate end,
                                   Map<String, List<Map<String, Object>>> todosByDay) {
        if (todo.getDueDate() == null) return false;
        String rule = todo.getRecurrence() == null ? null : todo.getRecurrence().toUpperCase();
        int interval = todo.getRecurrenceInterval() == null ? 1 : Math.max(1, todo.getRecurrenceInterval());
        LocalDate cursor = todo.getDueDate();
        boolean expanded = false;
        int safety = 0;
        while (!cursor.isAfter(end) && safety < 5000) {
            safety++;
            if (!cursor.isBefore(start)) {
                String key = cursor.format(DATE_FMT);
                List<Map<String, Object>> bucket = todosByDay.get(key);
                if (bucket == null) bucket = new ArrayList<>();
                bucket.add(todoView(todo, cursor));
                expanded = true;
            }
            cursor = switch (rule == null ? "" : rule) {
                case "DAILY" -> cursor.plusDays(interval);
                case "WEEKLY" -> cursor.plusWeeks(interval);
                case "MONTHLY" -> cursor.plusMonths(interval);
                case "YEARLY" -> cursor.plusYears(interval);
                default -> cursor.plusDays(interval);
            };
            if (cursor.isEqual(todo.getDueDate())) break;
        }
        return expanded;
    }

    private Map<String, Object> todoView(Todo t) {
        return todoView(t, t.getDueDate());
    }

    private Map<String, Object> todoView(Todo t, LocalDate displayDate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("note", t.getNote());
        m.put("priority", t.getPriority());
        m.put("completed", t.getCompleted());
        m.put("dueDate", displayDate);
        m.put("dueTime", t.getDueTime());
        m.put("remindAt", t.getRemindAt());
        m.put("recurrence", t.getRecurrence());
        m.put("recurrenceInterval", t.getRecurrenceInterval());
        m.put("completedAt", t.getCompletedAt());
        fillItemStats(t);
        m.put("itemCount", t.getItemCount());
        m.put("itemCompletedCount", t.getItemCompletedCount());
        return m;
    }

    private void fillItemStats(Todo t) {
        Long total = todoItemMapper.selectCount(new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getTodoId, t.getId())
                .eq(TodoItem::getDeleted, 0));
        Long done = todoItemMapper.selectCount(new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getTodoId, t.getId())
                .eq(TodoItem::getDeleted, 0)
                .eq(TodoItem::getCompleted, 1));
        t.setItemCount(total == null ? 0 : total.intValue());
        t.setItemCompletedCount(done == null ? 0 : done.intValue());
    }

    private Map<String, Object> noteView(Note n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("updateTime", n.getUpdateTime());
        m.put("createTime", n.getCreateTime());
        return m;
    }
}