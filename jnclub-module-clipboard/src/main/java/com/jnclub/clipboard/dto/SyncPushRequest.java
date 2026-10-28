package com.jnclub.clipboard.dto;

import lombok.Data;

import java.util.List;

/**
 * push 请求：客户端把本地待上传的变更一次性提交。
 *
 * <p>服务端对每条记录做 rev 乐观锁：rev 更大则接受，相等视为幂等重放，
 * 更小则判冲突并把服务端版本回传，由客户端决定怎么合并。
 */
@Data
public class SyncPushRequest {

    private List<ClipboardRecordDto> records;

    private List<ClipboardSettingDto> settings;

    /** 本次已上传到 dufs 的附件清单，用于把引用计数加上去 */
    private List<BlobRefDto> blobs;
}
