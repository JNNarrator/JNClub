package com.jnclub.music.user.controller;

import com.jnclub.music.common.ApiResponse;
import com.jnclub.music.user.api.PlaylistApi;
import com.jnclub.music.user.dto.PlaylistCreateRequest;
import com.jnclub.music.user.dto.PlaylistDTO;
import com.jnclub.music.user.dto.PlaylistDetailDTO;
import com.jnclub.music.user.dto.PlaylistRenameRequest;
import com.jnclub.music.user.dto.PlaylistTrackRequest;
import com.jnclub.music.user.service.PlaylistService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 歌单管理接口实现。
 */
@RestController
public class PlaylistController implements PlaylistApi {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public ApiResponse<List<PlaylistDTO>> listPlaylists(String deviceId) {
        return ApiResponse.success(playlistService.listPlaylists(deviceId));
    }

    @Override
    public ApiResponse<PlaylistDTO> createPlaylist(String deviceId, PlaylistCreateRequest request) {
        return ApiResponse.success(playlistService.createPlaylist(
                deviceId, request != null ? request.getName() : null));
    }

    @Override
    public ApiResponse<Void> renamePlaylist(String deviceId, Long id, PlaylistRenameRequest request) {
        playlistService.renamePlaylist(deviceId, id, request != null ? request.getName() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> deletePlaylist(String deviceId, Long id) {
        playlistService.deletePlaylist(deviceId, id);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<PlaylistDetailDTO> listTracks(String deviceId, Long id) {
        return ApiResponse.success(playlistService.listTracks(deviceId, id));
    }

    @Override
    public ApiResponse<Void> addTrack(String deviceId, Long id, PlaylistTrackRequest request) {
        playlistService.addTrack(deviceId, id, request != null ? request.getTrackId() : null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> removeTrack(String deviceId, Long id, String trackId) {
        playlistService.removeTrack(deviceId, id, trackId);
        return ApiResponse.success(null);
    }
}