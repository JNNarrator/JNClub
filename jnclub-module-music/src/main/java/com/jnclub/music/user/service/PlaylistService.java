package com.jnclub.music.user.service;

import com.jnclub.music.user.dto.PlaylistDTO;
import com.jnclub.music.user.dto.PlaylistDetailDTO;
import java.util.List;

/**
 * 匿名设备歌单服务。
 */
public interface PlaylistService {

    List<PlaylistDTO> listPlaylists(String deviceId);

    PlaylistDTO createPlaylist(String deviceId, String name);

    void renamePlaylist(String deviceId, Long playlistId, String name);

    void deletePlaylist(String deviceId, Long playlistId);

    PlaylistDetailDTO listTracks(String deviceId, Long playlistId);

    void addTrack(String deviceId, Long playlistId, String trackId);

    void removeTrack(String deviceId, Long playlistId, String trackId);
}