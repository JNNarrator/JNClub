package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办清单实体
 */
@Data
@TableName("t_todo")
public class Todo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String title;

    /** 备注（可选） */
    private String note;

    /** 优先级：0低 1中 2高 */
    private Integer priority;

    /** 截止日期（可选） */
    private LocalDate dueDate;

    /** 请求专用标记：显式清空截止日期（不落库） */
    @TableField(exist = false)
    private Boolean clearDueDate;

    /** 完成标记：0未完成 1已完成 */
    private Integer completed;

    /** 完成时间 */
    private LocalDateTime completedAt;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 软删除标记：0正常 1已删除 */
    private Integer deleted;
}
