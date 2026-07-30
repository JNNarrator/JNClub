package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片资源审计实体
 */
@Data
@TableName("t_note_asset")
public class NoteAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String originalName;

    private String storedKey;

    private String url;

    private Long size;

    private String mime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
