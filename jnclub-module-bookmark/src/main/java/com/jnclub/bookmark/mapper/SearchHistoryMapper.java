package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.SearchHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 搜索历史 Mapper
 */
@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {
}
