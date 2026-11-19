package com.jnclub.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 微信通道配置。
 *
 * <p>单用户自用场景下，这里直接给出"这个通道代表哪个 JNClub 用户在收发"，
 * 不需要 openid ↔ userId 的绑定体系。多用户场景再扩展成一张绑定表即可。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jnclub.wechat")
public class WechatProperties {

    /** 是否随应用启动自动拉起通道；关闭时只能通过接口手动登录。 */
    private boolean autoStart = true;

    /** 通道代表的 JNClub 用户标识，剪贴板数据按它隔离。 */
    private String userId = "jiangnan";

    /**
     * 允许写入剪贴板的白名单（微信 userId，形如 xxx@im.wechat）。
     * 为空表示不限制——<b>仅建议在私聊测试时这样做</b>，
     * 否则任何给 bot 发消息的人都能写入你的剪贴板。
     */
    private List<String> whitelist = List.of();

    /** 电脑端复制后，是否主动推送一条提醒到微信。 */
    private boolean notifyOnCopy = true;

    /** 主动提醒里正文的最大长度。 */
    private int notifyPreviewLength = 60;

    public boolean isAllowed(String fromUserId) {
        if (whitelist == null || whitelist.isEmpty()) {
            return true;
        }
        if (fromUserId == null) {
            return false;
        }
        return whitelist.stream().anyMatch(w -> fromUserId.equals(w) || fromUserId.startsWith(w));
    }

    public String whitelistText() {
        return whitelist == null ? "" : String.join(",", Arrays.asList(whitelist.toArray(new String[0])));
    }
}
