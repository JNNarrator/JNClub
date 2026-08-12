package com.jnclub.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.sso.SaTokenData;
import org.apache.ibatis.annotations.Mapper;

/**
 * Sa-Token 会话持久化 Mapper
 * <p>
 * 位于 *.mapper 包下，由全局 @MapperScan("com.jnclub.**.mapper") 扫描注册，同时标注 @Mapper 兜底。
 */
@Mapper
public interface SaTokenDataMapper extends BaseMapper<SaTokenData> {
}
