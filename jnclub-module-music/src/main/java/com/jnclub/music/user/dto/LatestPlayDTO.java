package com.jnclub.music.user.dto;

import com.jnclub.music.track.dto.TrackDTO;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 最近一次播放记录（跨设备「继续播放」）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestPlayDTO {

    private TrackDTO track;
    private Integer progressSeconds;
    private OffsetDateTime playedAt;
}