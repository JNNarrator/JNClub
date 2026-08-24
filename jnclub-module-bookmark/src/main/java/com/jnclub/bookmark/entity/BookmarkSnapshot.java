package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏网页快照实体（对应表 t_bookmark_snapshot，启动时幂等自建）
 * 收藏时抓取页面 HTML 存 dufs，失效后可查看快照兜底阅读
 */
@Data
@TableName("t_bookmark_snapshot")
public class BookmarkSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookmarkId;

    private String userId;

    /** dufs 存储路径（相对 base-url，如 /jnclub/snapshots/{userId}/{bookmarkId}.html） */
    private String dufsKey;

    /** 快照时页面标题 */
    private String title;

    /** 快照时 URL */
    private String url;

    /** 快照字节数 */
    private Integer size;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime capturedAt;

    /** 软删除标记：0正常 1已删除 */
    private Integer deleted;
}
