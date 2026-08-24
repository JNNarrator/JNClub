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

    /** 软删除标记：0正常 1回收站 */
    private Integer deleted;

    /** 失效检测状态：0未检测 1正常 2失效 */
    private Integer checkStatus;

    /** 最近失效检测时间 */
    private LocalDateTime checkedAt;

    /** 稍后读标记：0否 1是 */
    private Integer readLater;

    /** 阅读进度 0-100 */
    private Integer readProgress;

    /** 最近阅读时间 */
    private LocalDateTime readAt;
}
