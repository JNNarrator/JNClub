package com.jnclub.music.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.music.user.domain.SearchHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MusicSearchHistoryMapper extends BaseMapper<SearchHistory> {
}
