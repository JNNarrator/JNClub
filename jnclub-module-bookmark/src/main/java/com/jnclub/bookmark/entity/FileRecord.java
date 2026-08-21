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

    /** 文件内容 MD5（重复检测用；上传时计算，旧文件懒计算回填） */
    private String contentHash;

    private String url;

    private Long size;

    private String mime;

    /** 排序序号（同一目录内拖拽排序） */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 软删除标记：0正常 1回收站 */
    private Integer deleted;
}
