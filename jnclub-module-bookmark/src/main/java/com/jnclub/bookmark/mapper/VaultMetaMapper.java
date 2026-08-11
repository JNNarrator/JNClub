package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.VaultMeta;
import org.apache.ibatis.annotations.Mapper;

/**
 * 密码库主密钥元数据 Mapper
 */
@Mapper
public interface VaultMetaMapper extends BaseMapper<VaultMeta> {
}
