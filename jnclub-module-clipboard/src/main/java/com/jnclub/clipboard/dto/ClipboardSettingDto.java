package com.jnclub.clipboard.dto;

import lombok.Data;

/**
 * 设置在同步协议上的表示。
 *
 * <p>只有白名单键会被服务端接受（见 {@code ClipboardSyncService#SYNCABLE_SETTING_KEYS}），
 * 其余键静默丢弃并在响应里回报，避免设备私有配置跨端互相覆盖。
 */
@Data
public class ClipboardSettingDto {

    private String key;

    private String value;

    /** 墓碑时间；非空表示该键已被删除 */
    private Long deletedAt;

    private Long rev;

    private Long seq;
}
