package com.jnclub.clipboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 剪贴板设置同步条目。
 *
 * <p>只有白名单键（见 {@code ClipboardSyncService#SYNCABLE_SETTING_KEYS}）允许落库，
 * 其余键（窗口位置、快捷键、本机路径等）属于设备私有，同步过去只会造成互相覆盖。
 * 与条目共用同一个 seq 空间，因此 pull 一次即可拿到条目与设置的合并增量。
 */
@Data
@TableName("t_clipboard_setting")
public class ClipboardSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String settingKey;

    private String settingValue;

    /** 墓碑时间；非空表示该键已被删除 */
    private Long deletedAt;

    private Long rev;

    private Long seq;

    private LocalDateTime updatedAt;
}
