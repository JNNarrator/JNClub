package com.jnclub.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向歌单添加曲目请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistTrackRequest {

    private String trackId;
}