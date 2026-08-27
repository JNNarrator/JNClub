package com.jnclub.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建歌单请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistCreateRequest {

    private String name;
}