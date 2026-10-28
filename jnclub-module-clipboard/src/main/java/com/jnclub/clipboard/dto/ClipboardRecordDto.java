package com.jnclub.clipboard.dto;

import lombok.Data;

import java.util.List;

/**
 * 条目在同步协议上的表示。
 *
 * <p>字段与桌面端 Rust 的 {@code ClipboardEntry} / PWA 的 IndexedDB 记录一一对应，
 * 由客户端负责填充与解释；服务端只做透传与 seq/rev 管理，不改写业务字段。
 */
@Data
public class ClipboardRecordDto {

    /** 客户端生成的稳定标识（UUID），同一逻辑条目在所有设备上一致 */
    private String syncId;

    /** text / html / image / file_path */
    private String contentType;

    /** 正文；image 类型为 blob 的 sha256 */
    private String content;

    /** 正文归一化哈希，跨设备判重 */
    private Long contentHash;

    private String htmlContent;

    private String preview;

    private String sourceApp;

    private Boolean isPinned;

    private Integer pinnedOrder;

    private List<String> tags;

    private String note;

    private Integer useCount;

    /** 首次收录时间（客户端毫秒） */
    private Long createdAt;

    /** 最近使用时间（客户端毫秒） */
    private Long lastUsedAt;

    /** 列表排序键（客户端毫秒） */
    private Long sortAt;

    /** 墓碑时间；非空表示已删除，仍需下发对端 */
    private Long deletedAt;

    /** 客户端版本号，push 时做乐观锁 */
    private Long rev;

    /** 服务端同步游标，pull 时由服务端填充 */
    private Long seq;
}
