package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内提醒 Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}