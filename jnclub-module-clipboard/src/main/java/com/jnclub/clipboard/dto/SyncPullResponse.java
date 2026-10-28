package com.jnclub.clipboard.dto;

import lombok.Data;

import java.util.List;

/**
 * pull 响应：按 seq 升序返回游标之后的所有变更（条目 + 设置共用一个 seq 空间）。
 *
 * <p>{@code hasMore} 为 true 时客户端应立即再拉一次，而不是等下一个轮询周期——
 * 首次全量同步可能积累成千条记录，一次返回会撑爆响应体。
 */
@Data
public class SyncPullResponse {

    private List<ClipboardRecordDto> records;

    private List<ClipboardSettingDto> settings;

    /** 本批数据的最大 seq，客户端下次带上它作为 since */
    private Long nextSeq;

    private Boolean hasMore;
}
