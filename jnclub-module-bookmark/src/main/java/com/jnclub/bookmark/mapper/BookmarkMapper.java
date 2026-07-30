package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.Bookmark;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网页收藏 Mapper
 */
@Mapper
public interface BookmarkMapper extends BaseMapper<Bookmark> {
}
