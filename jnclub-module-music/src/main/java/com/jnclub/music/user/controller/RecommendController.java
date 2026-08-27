package com.jnclub.music.user.controller;

import com.jnclub.music.common.ApiResponse;
import com.jnclub.music.track.dto.TrackDTO;
import com.jnclub.music.user.api.RecommendApi;
import com.jnclub.music.user.service.RecommendService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 猜你喜欢接口实现。
 */
@RestController
public class RecommendController implements RecommendApi {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @Override
    public ApiResponse<List<TrackDTO>> recommend(String deviceId, Integer limit) {
        return ApiResponse.success(recommendService.recommend(deviceId, limit));
    }
}