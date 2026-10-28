package com.jnclub.clipboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剪贴板条目。
 *
 * <p>同步语义：{@code seq} 由服务端分配，客户端只按 {@code seq > lastSeq} 增量拉取，
 * 因此排序不依赖客户端时钟；{@code rev} 是客户端版本号，push 时用于乐观锁。
 * 时间字段（created_at / last_used_at / sort_at）是客户端毫秒时间戳，
 * 仅用于展示与列表排序，不参与同步判定。
 */
@Data
@TableName("t_clipboard_record")
public class ClipboardRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SSO 用户标识 */
    private String userId;

    /** 客户端生成的稳定标识，同一逻辑条目跨设备共享 */
    private String syncId;

    /** text / html / image / file_path */
    private String contentType;

    /** 正文；image 类型为 blob 的 sha256 引用 */
    private String content;

    /** 正文归一化哈希，用于跨设备判重 */
    private Long contentHash;

    /** 富文本原始 HTML */
    private String htmlContent;

    private String preview;

    private String sourceApp;

    private Boolean isPinned;

    private Integer pinnedOrder;

    /** JSON 数组字符串，如 ["工作","代码"] */
    private String tags;

    private String note;

    private Integer useCount;

    private Long createdAt;

    private Long lastUsedAt;

    private Long sortAt;

    /** 墓碑时间；非空表示已删除，但仍需下发对端以便对端删除 */
    private Long deletedAt;

    private Long rev;

    private Long seq;

    private LocalDateTime updatedAt;
}
