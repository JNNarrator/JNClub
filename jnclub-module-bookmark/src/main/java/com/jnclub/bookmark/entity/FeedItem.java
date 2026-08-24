package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RSS 条目实体（对应表 t_feed_item，启动时幂等自建）
 * guid 在源内唯一，(feed_id, guid) 唯一索引防重复入库
 */
@Data
@TableName("t_feed_item")
public class FeedItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long feedId;

    private String userId;

    /** 条目唯一标识（源内） */
    private String guid;

    private String title;

    /** 原文链接 */
    private String link;

    private String author;

    /** 摘要 */
    private String summary;

    /** 全文（HTML） */
    private String content;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime fetchedAt;

    /** 已读：0未读 1已读 */
    private Integer readFlag;

    /** 星标：0否 1是 */
    private Integer starred;

    /** 软删除标记：0正常 1已删除 */
    private Integer deleted;
}
