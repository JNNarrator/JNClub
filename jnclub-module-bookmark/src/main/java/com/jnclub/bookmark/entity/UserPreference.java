package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户偏好实体 — 通用 KV（JSON 值），供模块/视图记忆等场景复用
 */
@Data
@TableName("t_user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    /**
     * 偏好键（规范：模块.场景，如 module.activeModule、view.notes）
     */
    private String prefKey;

    /**
     * 偏好值（JSON 字符串）
     */
    private String prefValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
