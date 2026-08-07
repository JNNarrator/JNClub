package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 云盘文件 Mapper
 */
@Mapper
public interface FileMapper extends BaseMapper<FileRecord> {
}
