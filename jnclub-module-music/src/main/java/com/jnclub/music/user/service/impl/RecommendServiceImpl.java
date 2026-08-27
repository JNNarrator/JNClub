package com.jnclub.music.user.service.impl;

import com.jnclub.music.common.PageResponse;
import com.jnclub.music.track.dto.TrackDTO;
import com.jnclub.music.track.service.TrackService;
import com.jnclub.music.user.dto.HistoryTrackDTO;
import com.jnclub.music.user.service.FavoriteService;
import com.jnclub.music.user.service.HistoryService;
import com.jnclub.music.user.service.RecommendService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 猜你喜欢实现：
 * <ol>
 *   <li>用户收藏曲目（最近收藏优先）</li>
 *   <li>播放历史最近曲目</li>
 *   <li>随机补足到 limit</li>
 * </ol>
 * 纯规则实现，不新增表。
 */
@Service
public class RecommendServiceImpl extends UserDataSupport implements RecommendService {

    private final FavoriteService favoriteService;
    private final HistoryService historyService;
    private final TrackService trackService;

    public RecommendServiceImpl(TrackService trackService,
                                FavoriteService favoriteService,
                                HistoryService historyService) {
        super(trackService);
        this.trackService = trackService;
        this.favoriteService = favoriteService;
        this.historyService = historyService;
    }

    @Override
    public List<TrackDTO> recommend(String deviceId, Integer limit) {
        int normalizedLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 50);
        Set<String> trackIds = new LinkedHashSet<>();

        // 1. 收藏
        try {
            PageResponse<TrackDTO> favorites = favoriteService.listFavorites(deviceId, 1, 50);
            if (favorites.getItems() != null) {
                for (TrackDTO track : favorites.getItems()) {
                    if (track != null && track.getTrackId() != null) {
                        trackIds.add(track.getTrackId());
                    }
                }
            }
        } catch (Exception ignored) {
            // 收藏查询失败不阻塞推荐
        }

        // 2. 播放历史
        try {
            PageResponse<HistoryTrackDTO> history = historyService.listHistory(deviceId, 1, 50);
            if (history.getItems() != null) {
                for (HistoryTrackDTO item : history.getItems()) {
                    if (item.getTrack() != null && item.getTrack().getTrackId() != null) {
                        trackIds.add(item.getTrack().getTrackId());
                    }
                }
            }
        } catch (Exception ignored) {
            // 历史查询失败不阻塞推荐
        }

        // 3. 随机补足
        if (trackIds.size() < normalizedLimit) {
            try {
                List<String> all = new ArrayList<>(trackService.getAllTrackIds());
                Collections.shuffle(all);
                for (String id : all) {
                    if (id != null) {
                        trackIds.add(id);
                    }
                    if (trackIds.size() >= normalizedLimit) {
                        break;
                    }
                }
            } catch (Exception ignored) {
                // 全量列表失败时按现有结果返回
            }
        }

        List<String> ordered = trackIds.stream().limit(normalizedLimit).toList();
        Map<String, TrackDTO> trackMap = loadTrackMap(ordered);
        return ordered.stream()
                .map(trackMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}