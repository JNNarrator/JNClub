package com.jnclub.music.user.service;

import com.jnclub.music.track.dto.TrackDTO;
import java.util.List;

/**
 * 猜你喜欢推荐服务（规则版：收藏优先后历史、随机补足）。
 */
public interface RecommendService {

    List<TrackDTO> recommend(String deviceId, Integer limit);
}