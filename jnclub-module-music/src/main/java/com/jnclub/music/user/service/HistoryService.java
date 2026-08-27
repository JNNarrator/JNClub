package com.jnclub.music.user.service;

import com.jnclub.music.common.PageResponse;
import com.jnclub.music.user.dto.HistoryTrackDTO;
import com.jnclub.music.user.dto.LatestPlayDTO;

/**
 * 播放历史业务接口。
 */
public interface HistoryService {

    PageResponse<HistoryTrackDTO> listHistory(String deviceId, Integer page, Integer pageSize);

    void recordPlay(String deviceId, String trackId, Integer progress);

    LatestPlayDTO latestPlay(String deviceId);

    void clearHistory(String deviceId);
}
