package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 便签实体
 */
@Data
@TableName("t_note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** 列表摘要（非表字段，由列表接口填充；详情接口不返回） */
    @TableField(exist = false)
    private String excerpt;

    private Long directoryId;

    private String userId;

    private Integer sortOrder;

    /** 置顶标记：0否 1是 */
    private Integer pinned;

    /** 归档标记：0正常 1已归档 */
    private Integer archived;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 软删除标记：0正常 1回收站 */
    private Integer deleted;
}
