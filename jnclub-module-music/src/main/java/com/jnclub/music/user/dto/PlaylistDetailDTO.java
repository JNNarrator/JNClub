package com.jnclub.music.user.dto;

import com.jnclub.music.track.dto.TrackDTO;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌单详情（含曲目）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistDetailDTO {

    private Long id;
    private String name;
    private OffsetDateTime createdAt;
    private List<TrackDTO> tracks;
}