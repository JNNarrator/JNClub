package com.jnclub.wechat.config;

import com.jnclub.wechat.persistence.ContextTokenStore;
import com.jnclub.wechat.runtime.WechatChannelManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时恢复微信通道。
 *
 * <p>顺序很重要（{@code @Order} 保证在建表之后）：先让 WechatTableInit
 * 建好表，再预热 context_token，最后才尝试用已保存的凭据拉起通道。
 * 否则会出现"表还没建就查 token"的启动告警，虽不影响最终结果，但会误导排查。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class WechatChannelStarter implements ApplicationRunner {

    private final WechatProperties properties;
    private final WechatChannelManager channelManager;
    private final ContextTokenStore contextTokenStore;

    @Override
    public void run(ApplicationArguments args) {
        contextTokenStore.warmUp();

        if (!properties.isAutoStart()) {
            log.info("微信通道自动启动已关闭（jnclub.wechat.auto-start=false）");
            return;
        }
        // userId 决定消息写进谁的剪贴板。配错不会报错，只会"看起来同步成功
        // 但对方看不到"，所以在这里直接拦住。
        if (properties.getUserId() == null || properties.getUserId().isBlank()) {
            log.error("微信通道未配置 jnclub.wechat.user-id，已跳过启动。"
                    + "请填成与该实例剪贴板实际使用的用户 id 一致（可在 t_clipboard_record.user_id 查）。");
            return;
        }
        try {
            boolean resumed = channelManager.resume();
            if (resumed) {
                log.info("微信通道已自动恢复");
            } else {
                log.info("微信通道未登录，请在 jn-clipboard 设置页扫码");
            }
        } catch (Exception e) {
            // 通道起不来不应影响应用其它功能。
            log.warn("微信通道自动恢复失败：{}", e.getMessage());
        }
    }
}
