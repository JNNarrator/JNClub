package com.jnclub.music.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匿名设备歌单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("music_playlist")
public class Playlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}