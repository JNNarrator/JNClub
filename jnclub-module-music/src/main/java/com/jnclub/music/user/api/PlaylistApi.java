package com.jnclub.music.user.api;

import com.jnclub.music.common.ApiResponse;
import com.jnclub.music.user.dto.PlaylistCreateRequest;
import com.jnclub.music.user.dto.PlaylistDTO;
import com.jnclub.music.user.dto.PlaylistDetailDTO;
import com.jnclub.music.user.dto.PlaylistRenameRequest;
import com.jnclub.music.user.dto.PlaylistTrackRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 歌单管理接口定义（匿名设备隔离）。
 */
@RequestMapping("/api/v1/playlists")
public interface PlaylistApi {

    @GetMapping
    ApiResponse<List<PlaylistDTO>> listPlaylists(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId);

    @PostMapping
    ApiResponse<PlaylistDTO> createPlaylist(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestBody PlaylistCreateRequest request);

    @PutMapping("/{id}")
    ApiResponse<Void> renamePlaylist(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id,
            @RequestBody PlaylistRenameRequest request);

    @DeleteMapping("/{id}")
    ApiResponse<Void> deletePlaylist(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id);

    @GetMapping("/{id}/tracks")
    ApiResponse<PlaylistDetailDTO> listTracks(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id);

    @PostMapping("/{id}/tracks")
    ApiResponse<Void> addTrack(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id,
            @RequestBody PlaylistTrackRequest request);

    @DeleteMapping("/{id}/tracks/{trackId}")
    ApiResponse<Void> removeTrack(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id,
            @PathVariable String trackId);
}