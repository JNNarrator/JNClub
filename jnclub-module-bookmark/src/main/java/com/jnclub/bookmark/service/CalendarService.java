package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日历服务 — 月视图聚合：待办（按截止日）+ 便签（按更新时间）
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final TodoMapper todoMapper;
    private final NoteMapper noteMapper;

    /**
     * 某月聚合数据。
     * - todos：dueDate 落在当月的待办（含已完成，前端勾选态展示）
     * - overdueTodos：当月之前未完成的待办（跨月逾期，置顶标注）
     * - notes：updateTime 落在当月且未归档的便签
     */
    public Map<String, Object> month(int year, int month) {
        String userId = StpUtil.getLoginIdAsString();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        Map<String, Object> result = new LinkedHashMap<>();

        // 当月待办（含已完成）
        List<Map<String, Object>> todos = new ArrayList<>();
        todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getUserId, userId)
                        .eq(Todo::getDeleted, 0)
                        .isNotNull(Todo::getDueDate)
                        .ge(Todo::getDueDate, start)
                        .le(Todo::getDueDate, end)
                        .orderByAsc(Todo::getDueDate)
                        .orderByDesc(Todo::getPriority))
                .forEach(t -> todos.add(todoView(t)));
        result.put("todos", todos);

        // 跨月逾期未完成（当月之前），置顶提醒
        List<Map<String, Object>> overdueTodos = new ArrayList<>();
        todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getUserId, userId)
                        .eq(Todo::getDeleted, 0)
                        .eq(Todo::getCompleted, 0)
                        .isNotNull(Todo::getDueDate)
                        .lt(Todo::getDueDate, start)
                        .orderByAsc(Todo::getDueDate))
                .forEach(t -> overdueTodos.add(todoView(t)));
        result.put("overdueTodos", overdueTodos);

        // 当月更新的便签（未归档）
        List<Map<String, Object>> notes = new ArrayList<>();
        noteMapper.selectList(new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getDeleted, 0)
                        .eq(Note::getArchived, 0)
                        .ge(Note::getUpdateTime, start.atStartOfDay())
                        .lt(Note::getUpdateTime, end.plusDays(1).atStartOfDay())
                        .orderByDesc(Note::getUpdateTime))
                .forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("updateTime", n.getUpdateTime());
                    m.put("createTime", n.getCreateTime());
                    notes.add(m);
                });
        result.put("notes", notes);

        return result;
    }

    private Map<String, Object> todoView(Todo t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("note", t.getNote());
        m.put("priority", t.getPriority());
        m.put("completed", t.getCompleted());
        m.put("dueDate", t.getDueDate());
        m.put("completedAt", t.getCompletedAt());
        return m;
    }
}
