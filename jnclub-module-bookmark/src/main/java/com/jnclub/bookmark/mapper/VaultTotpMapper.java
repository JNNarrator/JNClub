package com.jnclub.bookmark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.bookmark.entity.VaultTotp;
import org.apache.ibatis.annotations.Mapper;

/**
 * 密码库 TOTP 种子 Mapper
 */
@Mapper
public interface VaultTotpMapper extends BaseMapper<VaultTotp> {
}
