package com.jnclub.music.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.music.user.domain.PlaylistItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlaylistItemMapper extends BaseMapper<PlaylistItem> {
}