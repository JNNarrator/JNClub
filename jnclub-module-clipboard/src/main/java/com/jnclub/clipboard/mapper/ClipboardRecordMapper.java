package com.jnclub.clipboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.clipboard.entity.ClipboardRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 剪贴板条目 Mapper
 */
@Mapper
public interface ClipboardRecordMapper extends BaseMapper<ClipboardRecord> {
}
