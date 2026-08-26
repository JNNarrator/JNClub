package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Notification;
import com.jnclub.bookmark.mapper.NotificationMapper;
import com.jnclub.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内提醒服务 — 未读列表 / 已读 / 全部已读
 */
@Service
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    public List<Notification> list(int limit, boolean unreadOnly) {
        String userId = StpUtil.getLoginIdAsString();
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        LambdaQueryWrapper<Notification> qw = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(unreadOnly, Notification::getReadFlag, 0)
                .orderByDesc(Notification::getCreateTime)
                .last("LIMIT " + size);
        return list(qw);
    }

    public long unreadCount() {
        String userId = StpUtil.getLoginIdAsString();
        Long c = baseMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        return c == null ? 0 : c;
    }

    public void markRead(Long id) {
        Notification n = getById(id);
        if (n == null || !n.getUserId().equals(StpUtil.getLoginIdAsString())) {
            throw new BizException("提醒不存在或无权操作");
        }
        if (n.getReadFlag() == null || n.getReadFlag() == 0) {
            n.setReadFlag(1);
            updateById(n);
        }
    }

    public void markAllRead() {
        String userId = StpUtil.getLoginIdAsString();
        List<Notification> unread = list(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        for (Notification n : unread) {
            n.setReadFlag(1);
            updateById(n);
        }
    }
}