package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网页收藏实体
 */
@Data
@TableName("t_bookmark")
public class Bookmark {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String url;

    private String icon;

    private Long directoryId;

    private String userId;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
