package com.jnclub.music.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重命名歌单请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistRenameRequest {

    private String name;
}