package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办子任务实体
 */
@Data
@TableName("t_todo_item")
public class TodoItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属待办 ID */
    private Long todoId;

    private String userId;

    private String title;

    /** 完成：0未完成 1已完成 */
    private Integer completed;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 软删除：0正常 1已删除 */
    private Integer deleted;
}