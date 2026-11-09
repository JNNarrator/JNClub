package com.jnclub.clipboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剪贴板附件元数据（内容寻址）。
 *
 * <p>实体文件存 dufs，路径为 {@code jnclub/clipboard/<userId>/<sha256前2位>/<sha256>}。
 * 用内容哈希做键的好处：同一张图在多个条目、多台设备间只存一份，且天然幂等——
 * 重复上传不会产生副本，只把 {@code refCount} 加一。
 */
@Data
@TableName("t_clipboard_blob")
public class ClipboardBlob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    /** 内容 SHA-256（小写十六进制） */
    private String sha256;

    private Long byteSize;

    private String mime;

    /** 引用计数，条目删除时递减；为 0 且超期后由 GC 清理 dufs 实体 */
    private Integer refCount;

    /** 引用计数归零的时间；GC 只清理「归零超过保留期」的附件，null 表示仍有引用 */
    private LocalDateTime zeroedAt;

    private LocalDateTime createdAt;
}
