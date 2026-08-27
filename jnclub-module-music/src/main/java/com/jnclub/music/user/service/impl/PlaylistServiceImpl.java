package com.jnclub.music.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jnclub.music.common.enums.ErrorCode;
import com.jnclub.music.common.exception.BusinessException;
import com.jnclub.music.track.dto.TrackDTO;
import com.jnclub.music.track.service.TrackService;
import com.jnclub.music.user.domain.Playlist;
import com.jnclub.music.user.domain.PlaylistItem;
import com.jnclub.music.user.dto.PlaylistDTO;
import com.jnclub.music.user.dto.PlaylistDetailDTO;
import com.jnclub.music.user.mapper.PlaylistItemMapper;
import com.jnclub.music.user.mapper.PlaylistMapper;
import com.jnclub.music.user.service.PlaylistService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 歌单服务实现：归属校验 + 幂等添加 + 级联删除。
 */
@Service
public class PlaylistServiceImpl extends UserDataSupport implements PlaylistService {

    private final PlaylistMapper playlistMapper;
    private final PlaylistItemMapper playlistItemMapper;

    public PlaylistServiceImpl(TrackService trackService,
                               PlaylistMapper playlistMapper,
                               PlaylistItemMapper playlistItemMapper) {
        super(trackService);
        this.playlistMapper = playlistMapper;
        this.playlistItemMapper = playlistItemMapper;
    }

    @Override
    public List<PlaylistDTO> listPlaylists(String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        List<Playlist> playlists = playlistMapper.selectList(Wrappers.<Playlist>lambdaQuery()
                .eq(Playlist::getDeviceId, normalizedDeviceId)
                .orderByDesc(Playlist::getUpdatedAt));
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = playlists.stream().map(Playlist::getId).toList();
        Map<Long, Long> countByPlaylist = playlistItemMapper.selectList(
                        Wrappers.<PlaylistItem>lambdaQuery().in(PlaylistItem::getPlaylistId, ids))
                .stream()
                .collect(Collectors.groupingBy(PlaylistItem::getPlaylistId, Collectors.counting()));
        return playlists.stream()
                .map(p -> PlaylistDTO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .trackCount((int) (long) countByPlaylist.getOrDefault(p.getId(), 0L))
                        .createdAt(toOffsetDateTime(p.getCreatedAt()))
                        .build())
                .toList();
    }

    @Override
    public PlaylistDTO createPlaylist(String deviceId, String name) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedName = requireName(name);
        Playlist playlist = Playlist.builder()
                .deviceId(normalizedDeviceId)
                .name(normalizedName)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        playlistMapper.insert(playlist);
        return PlaylistDTO.builder()
                .id(playlist.getId())
                .name(normalizedName)
                .trackCount(0)
                .createdAt(toOffsetDateTime(playlist.getCreatedAt()))
                .build();
    }

    @Override
    public void renamePlaylist(String deviceId, Long playlistId, String name) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        Playlist playlist = requireOwnedPlaylist(normalizedDeviceId, playlistId);
        playlist.setName(requireName(name));
        playlistMapper.updateById(playlist);
    }

    @Override
    public void deletePlaylist(String deviceId, Long playlistId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        requireOwnedPlaylist(normalizedDeviceId, playlistId);
        playlistItemMapper.delete(Wrappers.<PlaylistItem>lambdaQuery()
                .eq(PlaylistItem::getPlaylistId, playlistId));
        playlistMapper.deleteById(playlistId);
    }

    @Override
    public PlaylistDetailDTO listTracks(String deviceId, Long playlistId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        Playlist playlist = requireOwnedPlaylist(normalizedDeviceId, playlistId);
        List<PlaylistItem> items = playlistItemMapper.selectList(Wrappers.<PlaylistItem>lambdaQuery()
                .eq(PlaylistItem::getPlaylistId, playlistId)
                .orderByAsc(PlaylistItem::getPosition)
                .orderByAsc(PlaylistItem::getId));
        List<String> trackIds = items.stream().map(PlaylistItem::getTrackId).toList();
        Map<String, TrackDTO> trackMap = loadTrackMap(trackIds);
        List<TrackDTO> tracks = items.stream()
                .map(PlaylistItem::getTrackId)
                .map(trackMap::get)
                .filter(Objects::nonNull)
                .toList();
        return PlaylistDetailDTO.builder()
                .id(playlistId)
                .name(playlist.getName())
                .createdAt(toOffsetDateTime(playlist.getCreatedAt()))
                .tracks(tracks)
                .build();
    }

    @Override
    public void addTrack(String deviceId, Long playlistId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        requireOwnedPlaylist(normalizedDeviceId, playlistId);
        String normalizedTrackId = requireTrackId(trackId);
        ensureTrackExists(normalizedTrackId);
        Long existingCount = playlistItemMapper.selectCount(Wrappers.<PlaylistItem>lambdaQuery()
                .eq(PlaylistItem::getPlaylistId, playlistId)
                .eq(PlaylistItem::getTrackId, normalizedTrackId));
        if (existingCount != null && existingCount > 0) {
            return; // 幂等：重复添加忽略
        }
        int maxPosition = playlistItemMapper.selectList(Wrappers.<PlaylistItem>lambdaQuery()
                        .eq(PlaylistItem::getPlaylistId, playlistId))
                .stream()
                .mapToInt(PlaylistItem::getPosition)
                .max()
                .orElse(-1);
        playlistItemMapper.insert(PlaylistItem.builder()
                .playlistId(playlistId)
                .trackId(normalizedTrackId)
                .position(maxPosition + 1)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public void removeTrack(String deviceId, Long playlistId, String trackId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        requireOwnedPlaylist(normalizedDeviceId, playlistId);
        if (!StringUtils.hasText(trackId)) {
            return;
        }
        playlistItemMapper.delete(Wrappers.<PlaylistItem>lambdaQuery()
                .eq(PlaylistItem::getPlaylistId, playlistId)
                .eq(PlaylistItem::getTrackId, trackId.trim()));
    }

    private Playlist requireOwnedPlaylist(String deviceId, Long playlistId) {
        if (playlistId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "歌单ID不能为空");
        }
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null || !deviceId.equals(playlist.getDeviceId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "歌单不存在");
        }
        return playlist;
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "歌单名称不能为空");
        }
        return name.trim();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}