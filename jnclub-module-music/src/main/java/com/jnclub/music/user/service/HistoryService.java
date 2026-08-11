package com.jnclub.music.user.service;

import com.jnclub.music.common.PageResponse;
import com.jnclub.music.user.dto.HistoryTrackDTO;

/**
 * 播放历史业务接口。
 */
public interface HistoryService {

    PageResponse<HistoryTrackDTO> listHistory(String deviceId, Integer page, Integer pageSize);

    void recordPlay(String deviceId, String trackId);

    void clearHistory(String deviceId);
}
