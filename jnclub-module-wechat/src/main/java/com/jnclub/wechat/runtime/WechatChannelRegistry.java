package com.jnclub.wechat.runtime;

import com.jnclub.wechat.bridge.WechatMessageHandler;
import com.jnclub.wechat.config.WechatUserConfig;
import com.jnclub.wechat.persistence.ContextTokenStore;
import com.jnclub.wechat.persistence.WechatConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信通道注册表：按 JNClub 用户管理各自的 {@link WechatChannelManager}。
 *
 * <h2>为什么要有一层注册表</h2>
 * 每个用户的 bot 就是他自己扫码登录的那个微信号，凭据、context_token、
 * 消息流都互不相干，因此只能"一人一实例"。注册表负责按需创建与复用，
 * 并把"谁在线"汇总出来给轮询器和启动恢复使用。
 */
@Slf4j
@Component
public class WechatChannelRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final ContextTokenStore contextTokenStore;
    private final WechatConfigStore configStore;
    private final ObjectProvider<WechatMessageHandler> messageHandlerProvider;

    private final Map<String, WechatChannelManager> channels = new ConcurrentHashMap<>();

    public WechatChannelRegistry(JdbcTemplate jdbcTemplate,
                                 ContextTokenStore contextTokenStore,
                                 WechatConfigStore configStore,
                                 ObjectProvider<WechatMessageHandler> messageHandlerProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextTokenStore = contextTokenStore;
        this.configStore = configStore;
        this.messageHandlerProvider = messageHandlerProvider;
    }

    /** 取该用户的通道，没有则创建（不自动登录）。 */
    public WechatChannelManager getOrCreate(String userId) {
        return channels.computeIfAbsent(userId, id -> new WechatChannelManager(
                id, jdbcTemplate, contextTokenStore, configStore, messageHandlerProvider));
    }

    /** 已存在的通道；未创建过返回 null。 */
    public WechatChannelManager peek(String userId) {
        return channels.get(userId);
    }

    /** 当前所有通道（含未连接的）。 */
    public List<WechatChannelManager> all() {
        return new ArrayList<>(channels.values());
    }

    /** 已连接的通道，供轮询器推送剪贴板变更。 */
    public List<WechatChannelManager> connected() {
        List<WechatChannelManager> out = new ArrayList<>();
        for (WechatChannelManager channel : channels.values()) {
            if (channel.isConnected()) {
                out.add(channel);
            }
        }
        return out;
    }

    /**
     * 启动时逐个恢复有凭据的用户。
     *
     * <p>只恢复配置里没被关掉的用户；某个用户失败不影响其他人——
     * 一个人的 token 失效不该让别人的通道也起不来。
     */
    public void resumeAll() {
        contextTokenStore.warmUp();
        List<String> users = configStore.usersWithCredentials();
        if (users.isEmpty()) {
            log.info("没有已登录的微信通道");
            return;
        }
        for (String userId : users) {
            try {
                WechatUserConfig config = configStore.load(userId);
                if (!config.isEnabled()) {
                    log.info("[{}] 通道已在配置中关闭，跳过恢复", userId);
                    continue;
                }
                boolean ok = getOrCreate(userId).resume();
                if (ok) {
                    log.info("[{}] 微信通道已自动恢复", userId);
                } else {
                    log.info("[{}] 微信通道未登录，需重新扫码", userId);
                }
            } catch (Exception e) {
                log.warn("[{}] 恢复失败：{}", userId, e.getMessage());
            }
        }
    }

    /** 停止并移除某个用户的通道。 */
    public void remove(String userId) {
        WechatChannelManager channel = channels.remove(userId);
        if (channel != null) {
            channel.stop();
        }
    }
}
