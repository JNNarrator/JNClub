package com.jnclub.clipboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.clipboard.entity.ClipboardBlob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 剪贴板附件 Mapper
 */
@Mapper
public interface ClipboardBlobMapper extends BaseMapper<ClipboardBlob> {
}
