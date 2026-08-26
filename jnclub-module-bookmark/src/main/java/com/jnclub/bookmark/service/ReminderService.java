package com.jnclub.bookmark.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Notification;
import com.jnclub.bookmark.entity.Todo;
import com.jnclub.bookmark.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办提醒调度 — 每分钟扫描一次到点未通知的提醒，写入站内提醒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderService {

    private final TodoMapper todoMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * ?")
    public void scanTodoReminders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Todo> todos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                    .eq(Todo::getDeleted, 0)
                    .eq(Todo::getCompleted, 0)
                    .le(Todo::getRemindAt, now)
                    .and(w -> w.eq(Todo::getRemindNotified, 0).or().isNull(Todo::getRemindNotified)));
            for (Todo t : todos) {
                Notification n = new Notification();
                n.setUserId(t.getUserId());
                n.setType("TODO_REMIND");
                n.setTitle("待办提醒：" + t.getTitle());
                n.setContent(buildContent(t));
                n.setRefType("todo");
                n.setRefId(t.getId());
                n.setReadFlag(0);
                notificationService.save(n);

                t.setRemindNotified(1);
                todoMapper.updateById(t);
            }
            if (!todos.isEmpty()) {
                log.info("待办提醒已生成 {} 条", todos.size());
            }
        } catch (Exception e) {
            log.warn("待办提醒扫描失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String buildContent(Todo t) {
        StringBuilder sb = new StringBuilder();
        if (t.getPriority() != null && t.getPriority() == 2) sb.append("[高优先级] ");
        if (t.getDueDate() != null) {
            sb.append("截止 ").append(t.getDueDate());
            if (t.getDueTime() != null) sb.append(' ').append(t.getDueTime());
        }
        if (t.getRecurrence() != null && !t.getRecurrence().isBlank()) {
            sb.append(" · 重复").append(t.getRecurrence());
        }
        return sb.length() == 0 ? "记得处理这条待办" : sb.toString();
    }
}