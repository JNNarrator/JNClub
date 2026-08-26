package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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

    /** 截止时间（可选，HH:mm:ss） */
    private LocalTime dueTime;

    /** 提醒时间（可选） */
    private LocalDateTime remindAt;

    /** 已生成提醒标记：0未提醒 1已提醒 */
    private Integer remindNotified;

    /** 重复规则：null/空=不重复，DAILY/WEEKLY/MONTHLY/YEARLY */
    private String recurrence;

    /** 重复间隔（配合 recurrence，默认 1） */
    private Integer recurrenceInterval;

    /** 请求专用标记：显式清空截止日期（不落库） */
    @TableField(exist = false)
    private Boolean clearDueDate;

    /** 请求专用标记：显式清空截止时间（不落库） */
    @TableField(exist = false)
    private Boolean clearDueTime;

    /** 请求专用标记：显式清空提醒时间（不落库） */
    @TableField(exist = false)
    private Boolean clearRemindAt;

    /** 完成标记：0未完成 1已完成 */
    private Integer completed;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 子任务列表（不落库，读取时填充） */
    @TableField(exist = false)
    private List<TodoItem> items;

    /** 子任务数量（不落库，读取时填充） */
    @TableField(exist = false)
    private Integer itemCount;

    /** 已完成子任务数量（不落库，读取时填充） */
    @TableField(exist = false)
    private Integer itemCompletedCount;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 软删除标记：0正常 1已删除 */
    private Integer deleted;
}
