package com.jnclub.common.sso;

import cn.dev33.satoken.sso.SaSsoManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * SSO 配置类
 */
@Configuration
public class SsoConfig {

    @Value("${sa-token.sso.server-url}")
    private String serverUrl;

    @Value("${sa-token.sso.client-url}")
    private String clientUrl;

    @Value("${sa-token.sso.secret-key}")
    private String secretKey;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // Sa-Token SSO 配置通过 application.yml 自动配置
        // 这里可以添加额外的初始化逻辑
    }
}
