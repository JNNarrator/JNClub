package com.jnclub.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信通道的<b>全局</b>配置。
 *
 * <p>多租户之后，"这个通道代表哪个用户、谁能写入、要不要推送"这些
 * 都是每用户的东西，存在 {@code t_wechat_config} 里（见 {@link WechatUserConfig}），
 * 不再放在这里。这里只剩与具体用户无关的进程级开关。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jnclub.wechat")
public class WechatProperties {

    /** 是否随应用启动自动恢复所有已登录用户的通道。 */
    private boolean autoStart = true;

    /** 主动提醒里正文的最大长度。 */
    private int notifyPreviewLength = 60;
}
