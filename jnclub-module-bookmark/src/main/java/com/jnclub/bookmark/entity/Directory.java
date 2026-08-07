package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 目录实体
 */
@Data
@TableName("t_directory")
public class Directory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    /**
     * 目录图标（预设 key，如 folder/bookmark/star 等；未选默认文件夹）
     */
    private String icon;

    /**
     * 目录类型：1=收藏夹目录  2=便签目录
     */
    private Integer type;

    private Integer sortOrder;

    private String userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<Directory> children;
}
