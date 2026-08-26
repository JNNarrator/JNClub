package com.jnclub.bookmark.controller;

import com.jnclub.bookmark.entity.Notification;
import com.jnclub.bookmark.service.NotificationService;
import com.jnclub.common.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站内提醒控制器
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 提醒列表：limit 默认 50，unreadOnly=true 只看未读 */
    @GetMapping
    public R<List<Notification>> list(@RequestParam(defaultValue = "50") int limit,
                                      @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return R.ok(notificationService.list(limit, unreadOnly));
    }

    /** 未读数 */
    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", notificationService.unreadCount()));
    }

    /** 单条已读 */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead();
        return R.ok();
    }
}