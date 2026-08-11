package com.jnclub.music.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 音乐模块装配配置
 *
 * <p>JNMusic 并入 JNClub 单体后不再拥有独立启动类，这里补齐原启动类上的全局开关：
 * <ul>
 *   <li>{@link EnableAsync}：直链异步刷新（TrackCacheService 的 @Async 依赖）</li>
 * </ul>
 * {@link EnableCaching} 由 {@code com.jnclub.music.common.config.CacheConfig} 提供；
 * 分页插件由 jnclub-module-bookmark 的 MybatisPlusConfig 统一注册，音乐 Mapper 共享。
 */
@Configuration
@EnableAsync
public class MusicModuleConfig {
}
