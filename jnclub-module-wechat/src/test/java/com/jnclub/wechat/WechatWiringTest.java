package com.jnclub.wechat;

import com.jnclub.wechat.bridge.ClipboardBridgeService;
import com.jnclub.wechat.bridge.ClipboardStreamListener;
import com.jnclub.wechat.bridge.WechatMessageHandler;
import com.jnclub.wechat.runtime.WechatChannelManager;
import com.jnclub.wechat.runtime.WechatChannelRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 装配自检：确认微信模块的 Bean 之间没有循环依赖。
 *
 * <h2>为什么要有这个测试</h2>
 * 这个模块曾因 <b>ChannelManager ↔ MessageHandler 的构造期循环</b>
 * 导致整个应用启动失败，并被自动部署脚本回滚。
 * 编译、单元测试全过——因为环只在 Spring 装配时才暴露。
 *
 * <p>多租户改造后 ChannelManager 不再由 Spring 直接创建，
 * 而由 {@link WechatChannelRegistry} 按需 new。因此这里除了验证
 * 上下文能起来，还要验证注册表真的能造出一个可用的通道实例。
 */
class WechatWiringTest {

    @Configuration
    static class TestConfig {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate(new SimpleDriverDataSource(
                    new org.h2.Driver(), "jdbc:h2:mem:wiring;DB_CLOSE_DELAY=-1", "sa", ""));
        }

        // ---- 剪贴板模块的依赖占位 ----
        // 装配自检只关心"能不能创建出来"，不关心行为，所以用 mock。
        @Bean
        com.jnclub.clipboard.mapper.ClipboardRecordMapper clipboardRecordMapper() {
            return org.mockito.Mockito.mock(com.jnclub.clipboard.mapper.ClipboardRecordMapper.class);
        }

        @Bean
        com.jnclub.clipboard.mapper.ClipboardSettingMapper clipboardSettingMapper() {
            return org.mockito.Mockito.mock(com.jnclub.clipboard.mapper.ClipboardSettingMapper.class);
        }

        @Bean
        com.jnclub.clipboard.mapper.ClipboardBlobMapper clipboardBlobMapper() {
            return org.mockito.Mockito.mock(com.jnclub.clipboard.mapper.ClipboardBlobMapper.class);
        }

        @Bean
        com.jnclub.clipboard.mapper.ClipboardUserSeqMapper clipboardUserSeqMapper() {
            return org.mockito.Mockito.mock(com.jnclub.clipboard.mapper.ClipboardUserSeqMapper.class);
        }
    }

    @Test
    @DisplayName("微信模块的 Bean 能在不使用循环引用开关的情况下装配完成")
    void contextLoadsWithoutCircularReference() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            // 关键：不开启 allow-circular-references，让环直接暴露
            ctx.register(TestConfig.class);
            ctx.scan("com.jnclub.wechat", "com.jnclub.clipboard.service");
            ctx.refresh();

            assertNotNull(ctx.getBean(WechatChannelRegistry.class), "Registry 应可装配");
            assertNotNull(ctx.getBean(WechatMessageHandler.class), "MessageHandler 应可装配");
            assertNotNull(ctx.getBean(ClipboardStreamListener.class), "StreamListener 应可装配");
            assertNotNull(ctx.getBean(ClipboardBridgeService.class), "BridgeService 应可装配");
        }
    }

    @Test
    @DisplayName("注册表能为不同用户各造一个独立的通道实例")
    void registryCreatesIndependentChannels() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(TestConfig.class);
            ctx.scan("com.jnclub.wechat", "com.jnclub.clipboard.service");
            ctx.refresh();

            WechatChannelRegistry registry = ctx.getBean(WechatChannelRegistry.class);
            WechatChannelManager a = registry.getOrCreate("1");
            WechatChannelManager b = registry.getOrCreate("2");

            assertEquals("1", a.userId());
            assertEquals("2", b.userId());
            // 多租户的核心：两个用户绝不能拿到同一个实例，否则凭据与会话会互相串
            assertNotSame(a, b);
            // 同一个用户重复取应复用，避免重复建立长轮询
            assertSame(a, registry.getOrCreate("1"));
            assertEquals(2, registry.all().size());
        }
    }
}
