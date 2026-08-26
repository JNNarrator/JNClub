package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搜索历史（服务端）
 */
@Data
@TableName("t_search_history")
public class SearchHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String keyword;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
