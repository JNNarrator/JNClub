package com.jnclub.clipboard.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 每用户同步游标（单行）。
 *
 * <p>用 {@code UPDATE ... SET seq = seq + n} 的原子自增拿号，避免「先查再写」的竞态：
 * 多设备并发 push 时若用读-改-写，两台设备可能拿到同一个 seq，
 * 导致 pull 的 {@code seq > lastSeq} 漏掉其中一条。
 */
@Data
@TableName("t_clipboard_user_seq")
public class ClipboardUserSeq {

    @TableId
    private String userId;

    private Long seq;

    private LocalDateTime updatedAt;
}
