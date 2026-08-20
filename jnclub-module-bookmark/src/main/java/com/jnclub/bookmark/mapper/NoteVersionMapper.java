package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.NoteVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 便签历史版本 Mapper
 */
@Mapper
public interface NoteVersionMapper extends BaseMapper<NoteVersion> {
}
