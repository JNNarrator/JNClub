package com.jnclub.music.user.api;

import com.jnclub.music.common.ApiResponse;
import com.jnclub.music.track.dto.TrackDTO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 猜你喜欢接口定义（匿名设备隔离）。
 */
@RequestMapping("/api/v1/recommend")
public interface RecommendApi {

    @GetMapping
    ApiResponse<List<TrackDTO>> recommend(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit);
}