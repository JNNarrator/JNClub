package com.jnclub.music.config;

import org.springframework.context.annotation.Configuration;

/**
 * 音乐模块装配配置
 *
 * <p>JNMusic 并入 JNClub 单体后不再拥有独立启动类，这里保留模块级装配入口。</p>
 *
 * <p>说明：此处原先标注 {@code @EnableAsync}，用于 TrackCacheService 的直链异步刷新。
 * 但该服务的两处 {@code @Async} 均已改为显式线程池提交（{@code roundExecutor}/{@code refreshExecutor}），
 * 全项目已无 {@code @Async} 消费者，故移除该开关，避免继续依赖隐式代理行为。</p>
 *
 * <p>{@code @EnableCaching} 由 {@code com.jnclub.music.common.config.CacheConfig} 提供；
 * 分页插件由 jnclub-module-bookmark 的 MybatisPlusConfig 统一注册，音乐 Mapper 共享。</p>
 */
@Configuration
public class MusicModuleConfig {
}
