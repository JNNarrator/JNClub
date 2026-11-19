package com.jnclub.wechat.config;

import com.jnclub.wechat.runtime.WechatChannelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时恢复所有已登录用户的微信通道。
 *
 * <p>{@code @Order(100)} 保证在建表之后执行：先让 WechatTableInit 建好表，
 * 再预热 context_token，最后才逐个尝试恢复。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class WechatChannelStarter implements ApplicationRunner {

    private final WechatProperties properties;
    private final WechatChannelRegistry registry;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoStart()) {
            log.info("微信通道自动启动已关闭（jnclub.wechat.auto-start=false）");
            return;
        }
        try {
            registry.resumeAll();
        } catch (Exception e) {
            // 通道起不来不应影响应用其它功能。
            log.warn("微信通道自动恢复失败：{}", e.getMessage());
        }
    }
}
