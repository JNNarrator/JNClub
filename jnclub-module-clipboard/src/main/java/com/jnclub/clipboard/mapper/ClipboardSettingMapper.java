package com.jnclub.clipboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.clipboard.entity.ClipboardSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 剪贴板设置同步 Mapper
 */
@Mapper
public interface ClipboardSettingMapper extends BaseMapper<ClipboardSetting> {
}
