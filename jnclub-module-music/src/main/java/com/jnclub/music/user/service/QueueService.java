package com.jnclub.music.user.service;

import com.jnclub.music.user.dto.QueueItemDTO;
import com.jnclub.music.user.dto.QueueItemRequest;
import java.util.List;

/**
 * 播放队列业务接口。
 */
public interface QueueService {

    List<QueueItemDTO> listQueue(String deviceId);

    void saveQueue(String deviceId, List<QueueItemRequest> items);

    void appendTrack(String deviceId, String trackId);

    void removeTrack(String deviceId, String trackId);
}
