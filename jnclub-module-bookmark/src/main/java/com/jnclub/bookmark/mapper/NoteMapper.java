package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.Note;
import org.apache.ibatis.annotations.Mapper;

/**
 * 便签 Mapper
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {
}
