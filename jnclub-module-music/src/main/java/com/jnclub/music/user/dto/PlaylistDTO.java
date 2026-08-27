package com.jnclub.music.user.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌单列表条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistDTO {

    private Long id;
    private String name;
    private Integer trackCount;
    private OffsetDateTime createdAt;
}