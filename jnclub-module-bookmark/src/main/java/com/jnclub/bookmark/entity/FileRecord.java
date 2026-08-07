package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 云盘文件实体（对应表 t_file，目录复用 t_directory type=3）
 */
@Data
@TableName("t_file")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long directoryId;

    private String userId;

    private String originalName;

    private String storedKey;

    private String url;

    private Long size;

    private String mime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
