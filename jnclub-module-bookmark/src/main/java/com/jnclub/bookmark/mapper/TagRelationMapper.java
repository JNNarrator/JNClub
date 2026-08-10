package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.TagRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签关联 Mapper
 */
@Mapper
public interface TagRelationMapper extends BaseMapper<TagRelation> {
}
