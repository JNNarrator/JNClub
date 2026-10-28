package com.jnclub.clipboard.dto;

import lombok.Data;

import java.util.List;

/**
 * push 响应：告诉客户端哪些条目被接受、哪些因版本冲突被拒。
 *
 * <p>被拒的条目连同服务端当前版本一起回传（{@link ConflictDto}），
 * 客户端据此更新本地 rev 后重试，或走「后写覆盖」策略。
 */
@Data
public class SyncPushResponse {

    /** 被接受的条目 syncId */
    private List<String> accepted;

    /** 被拒的条目及其服务端版本 */
    private List<ConflictDto> conflicts;

    /** 白名单外的设置键（静默丢弃，但回报给客户端便于排查） */
    private List<String> rejectedSettings;

    /** 推送完成后本用户的最大 seq */
    private Long nextSeq;
}
