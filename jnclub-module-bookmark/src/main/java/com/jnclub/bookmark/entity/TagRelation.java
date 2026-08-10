package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签关联实体（对应表 t_tag_relation，多对多）
 */
@Data
@TableName("t_tag_relation")
public class TagRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tagId;

    /** 关联类型：bookmark=收藏 note=便签 */
    private String refType;

    /** 关联记录 ID（t_bookmark.id / t_note.id） */
    private Long refId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
