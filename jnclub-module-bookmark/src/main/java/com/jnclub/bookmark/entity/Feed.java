package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RSS 订阅源实体（对应表 t_feed，启动时幂等自建）
 */
@Data
@TableName("t_feed")
public class Feed {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    /** 订阅源地址 */
    private String url;

    /** 源标题 */
    private String title;

    /** 站点地址 */
    private String siteUrl;

    /** 站点图标 */
    private String icon;

    /** 最近抓取时间 */
    private LocalDateTime lastFetchedAt;

    /** 抓取间隔（分钟），默认 30 */
    private Integer fetchIntervalMin;

    /** 软删除标记：0正常 1已删除 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
