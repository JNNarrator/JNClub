package com.jnclub.music.user.controller;

import com.jnclub.music.common.ApiResponse;
import com.jnclub.music.common.PageResponse;
import com.jnclub.music.track.dto.TrackDTO;
import com.jnclub.music.user.api.FavoriteApi;
import com.jnclub.music.user.dto.ExistsDTO;
import com.jnclub.music.user.dto.TrackIdRequest;
import com.jnclub.music.user.service.FavoriteService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1 收藏同步接口。
 */
@RestController
public class FavoriteController implements FavoriteApi {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Override
    public ApiResponse<PageResponse<TrackDTO>> listFavorites(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            Integer page,
            Integer pageSize) {
        return ApiResponse.success(favoriteService.listFavorites(deviceId, page, pageSize));
    }

    @Override
    public ApiResponse<Void> addFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            TrackIdRequest request) {
        favoriteService.addFavorite(deviceId, request != null ? request.getTrackId() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> removeFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            String trackId) {
        favoriteService.removeFavorite(deviceId, trackId);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<ExistsDTO> existsFavorite(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            String trackId) {
        return ApiResponse.success(ExistsDTO.builder()
                .exists(favoriteService.existsFavorite(deviceId, trackId))
                .build());
    }
}
