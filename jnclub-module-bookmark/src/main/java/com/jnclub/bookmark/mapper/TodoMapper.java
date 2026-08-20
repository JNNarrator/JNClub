package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.Todo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办 Mapper
 */
@Mapper
public interface TodoMapper extends BaseMapper<Todo> {
}
