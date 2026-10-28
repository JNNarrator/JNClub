package com.jnclub.clipboard.dto;

import lombok.Data;

/**
 * 版本冲突：客户端提交的 rev 不大于服务端当前 rev，说明服务端已有更新的版本。
 */
@Data
public class ConflictDto {

    private String syncId;

    /** 服务端当前版本，客户端据此覆盖本地 rev */
    private Long serverRev;

    /** 服务端当前条目内容，供客户端做字段级合并 */
    private ClipboardRecordDto serverRecord;
}
