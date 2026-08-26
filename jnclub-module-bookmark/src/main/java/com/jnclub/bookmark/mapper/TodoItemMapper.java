package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.TodoItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办子任务 Mapper
 */
@Mapper
public interface TodoItemMapper extends BaseMapper<TodoItem> {
}