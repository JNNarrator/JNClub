package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 便签历史版本快照（对应表 t_note_version，启动时幂等自建）
 * 每次内容变更自动保存上一版快照，可查看/回滚
 */
@Data
@TableName("t_note_version")
public class NoteVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 便签ID */
    private Long noteId;

    private String userId;

    /** 快照标题 */
    private String title;

    /** 快照内容（Markdown 原文） */
    private String content;

    /** 版本号（自增，从 1 开始） */
    private Integer versionNo;

    private LocalDateTime createTime;
}
