package com.jnclub.wechat;

import com.jnclub.wechat.bridge.ClipboardBridgeService;
import com.jnclub.wechat.bridge.ClipboardStreamListener;
import com.jnclub.wechat.bridge.WechatMessageHandler;
import com.jnclub.wechat.runtime.WechatChannelManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 装配自检：确认微信模块的 Bean 之间没有循环依赖。
 *
 * <h2>为什么要有这个测试</h2>
 * 这个模块曾经因为 <b>ChannelManager ↔ MessageHandler 的构造期循环</b>
 * 导致整个应用启动失败，并被自动部署脚本回滚。
 * 编译、单元测试全过——因为环只在 Spring 装配时才暴露。
 *
 * <p>真实事故的代价是"生产回滚一次"，所以这里用最小上下文把装配本身
 * 纳入测试范围：只要有人再把依赖直接注入回去，这个测试会先失败。
 *
 * <p>用 H2 + 不启动 Web 容器，避免依赖真实数据库与端口。
 */
class WechatWiringTest {

    /**
     * 最小可用数据源：只为让 JdbcTemplate 能被创建，
     * 装配阶段不会真的执行 SQL（表初始化是 ApplicationRunner，这里不跑）。
     */
    @Configuration
    static class TestConfig {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate(new SimpleDriverDataSource(
                    new org.h2.Driver(), "jdbc:h2:mem:wiring;DB_CLOSE_DELAY=-1", "sa", ""));
        }

        // ---- 剪贴板模块的依赖占位 ----
        // 装配自检只关心"能不能创建出来"，不关心它们的行为，
        // 所以用 mock 而不是真连数据库。
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

            assertNotNull(ctx.getBean(WechatChannelManager.class), "ChannelManager 应可装配");
            assertNotNull(ctx.getBean(WechatMessageHandler.class), "MessageHandler 应可装配");
            assertNotNull(ctx.getBean(ClipboardStreamListener.class), "StreamListener 应可装配");
            assertNotNull(ctx.getBean(ClipboardBridgeService.class), "BridgeService 应可装配");
        }
    }

    @Test
    @DisplayName("ChannelManager 用 ObjectProvider 延迟取 Handler，而不是直接持有")
    void channelManagerDefersHandlerLookup() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(TestConfig.class);
            ctx.scan("com.jnclub.wechat", "com.jnclub.clipboard.service");
            ctx.refresh();

            WechatChannelManager manager = ctx.getBean(WechatChannelManager.class);
            // 真正取一次：如果 Provider 没接对，这里会失败
            assertNotNull(manager.state());
        }
    }
}
