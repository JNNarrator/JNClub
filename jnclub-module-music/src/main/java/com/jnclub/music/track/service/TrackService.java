package com.jnclub.music.track.service;

import com.jnclub.music.common.PageResponse;
import com.jnclub.music.track.dto.MediaUrlDTO;
import com.jnclub.music.track.dto.TrackDTO;
import com.jnclub.music.track.dto.TrackSummaryDTO;
import com.jnclub.music.track.dto.TrackWithUrlDTO;
import java.util.List;
import java.util.Map;

public interface TrackService {

    java.util.List<String> getAllTrackIds();

    PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize);

    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize);

    PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize, boolean refresh);

    TrackDTO getTrackById(String trackId);

    PageResponse<TrackDTO> getTracksByIds(List<String> ids);

    MediaUrlDTO getMediaUrl(String trackId);

    /**
     * 强制刷新播放直链（绕过 @Cacheable 和有效期检查，直接调蓝奏云拉取并回写 MySQL）。
     * 用于缓存刷新任务：即使 url_expires_at 尚未过期，也强制获取新直链。
     * @param trackId 歌曲ID
     * @return 新的播放直链；拉取失败返回 null
     */
    MediaUrlDTO refreshMediaUrl(String trackId);

    /**
     * 批量获取播放直链
     * @param trackIds trackId列表
     * @return trackId -> MediaUrlDTO 的映射
     */
    Map<String, MediaUrlDTO> getMediaUrls(List<String> trackIds);

    String getLyrics(String trackId);

    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize);

    PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize, boolean refresh);

    PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize);
}
