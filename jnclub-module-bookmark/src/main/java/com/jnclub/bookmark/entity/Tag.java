package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签实体（对应表 t_tag）
 */
@Data
@TableName("t_tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String name;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 标签下关联记录数（查询时统计，非表字段） */
    @TableField(exist = false)
    private Long count;
}
