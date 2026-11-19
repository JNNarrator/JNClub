package com.jnclub.wechat.runtime;

import com.jnclub.wechat.ilink.WechatBotSdk;
import com.jnclub.wechat.ilink.auth.LoginManager;
import com.jnclub.wechat.ilink.cache.ContextTokenCache;
import com.jnclub.wechat.ilink.listener.SimpleMessageListener;
import com.jnclub.wechat.ilink.model.auth.Credentials;
import com.jnclub.wechat.ilink.model.message.WechatMessage;
import com.jnclub.wechat.ilink.model.response.QrcodeResponse;
import com.jnclub.wechat.ilink.model.response.QrcodeStatus;
import com.jnclub.wechat.ilink.model.response.QrcodeStatusResponse;
import com.jnclub.wechat.persistence.ContextTokenStore;
import com.jnclub.wechat.persistence.JdbcBufferStore;
import com.jnclub.wechat.persistence.JdbcCredentialsStore;
import com.jnclub.wechat.bridge.WechatMessageHandler;
import com.jnclub.wechat.config.WechatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信通道的生命周期管理：扫码登录、启动/停止监听、发送消息。
 *
 * <p>整个应用只维护一个通道实例（单用户自用），因此这里是一个单例的
 * 状态持有者，而不是每用户一份。
 *
 * <p>状态机：{@code DOWN → WAITING_SCAN → CONNECTED}，任一步失败回到 DOWN。
 * 前端轮询 {@code /api/wechat/status} 展示。
 */
@Slf4j
@Component
public class WechatChannelManager {

    public enum State { DOWN, WAITING_SCAN, CONNECTED }

    private final JdbcTemplate jdbcTemplate;
    private final ContextTokenStore contextTokenStore;
    private final WechatProperties properties;
    /**
     * 用 ObjectProvider 延迟取，而不是直接注入。
     *
     * <p>MessageHandler 需要 ChannelManager 来回复消息，ChannelManager 又需要
     * Handler 来分发收到的消息——直接构造注入会形成环，Spring 启动时报
     * "Requested bean is currently in creation"。这不是靠 allow-circular-references
     * 该开的开关：那个开关在有代理（AOP）时仍可能失效，而且把环留在了架构里。
     * 在这里把"取用时机"推迟到真正收到消息时，环就不存在了。
     */
    private final ObjectProvider<WechatMessageHandler> messageHandlerProvider;

    private final AtomicReference<State> state = new AtomicReference<>(State.DOWN);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private volatile WechatBotSdk sdk;
    private volatile String loggedInUserId;
    private volatile String pendingQrcode;

    public WechatChannelManager(JdbcTemplate jdbcTemplate,
                                ContextTokenStore contextTokenStore,
                                WechatProperties properties,
                                ObjectProvider<WechatMessageHandler> messageHandlerProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextTokenStore = contextTokenStore;
        this.properties = properties;
        this.messageHandlerProvider = messageHandlerProvider;
    }

    /**
     * 剪贴板归属用户是否未配置。
     *
     * <p>配错时不会有任何报错：消息照常写入、手机照常收到"已同步"，
     * 但数据落在别人的 user_id 下，网页端永远看不到。所以这里宁可拒绝启用。
     */
    private void requireUserId() {
        String userId = properties.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException(
                    "未配置 jnclub.wechat.user-id：微信消息会写进错误的剪贴板空间");
        }
    }

    public State state() {
        return state.get();
    }

    public String lastError() {
        return lastError.get();
    }

    public String loggedInUserId() {
        return loggedInUserId;
    }

    public boolean isConnected() {
        return state.get() == State.CONNECTED && sdk != null;
    }

    public String pendingQrcode() {
        return pendingQrcode;
    }

    /**
     * 用已保存的凭据尝试恢复通道（应用启动时调用）。
     *
     * @return 是否恢复成功
     */
    public synchronized boolean resume() {
        if (properties.getUserId() == null || properties.getUserId().isBlank()) {
            log.error("未配置 jnclub.wechat.user-id，跳过微信通道恢复");
            return false;
        }
        try {
            WechatBotSdk candidate = newSdk();
            if (!candidate.isAuthenticated()) {
                log.info("没有可用的微信凭据，需要扫码登录");
                return false;
            }
            startListening(candidate);
            return true;
        } catch (Exception e) {
            log.warn("恢复微信通道失败：{}", e.getMessage());
            lastError.set(e.getMessage());
            return false;
        }
    }

    /** 请求登录二维码；已登录则直接返回空。 */
    public synchronized String requestQrcode() throws Exception {
        requireUserId();
        if (isConnected()) {
            return null;
        }
        WechatBotSdk candidate = newSdk();
        if (candidate.isAuthenticated()) {
            startListening(candidate);
            return null;
        }
        QrcodeResponse qrcode = candidate.getQrcode();
        this.sdk = candidate;
        this.pendingQrcode = qrcode.getQrcode();
        this.state.set(State.WAITING_SCAN);
        // 登录在后台线程等扫码，避免阻塞 HTTP 请求
        Thread.ofVirtual().name("wechat-login").start(() -> awaitScan(candidate, qrcode.getQrcode()));
        return qrcode.getQrcodeImgContent();
    }

    private void awaitScan(WechatBotSdk candidate, String qrcodeToken) {
        try {
            Credentials credentials = candidate.loginWithQrcode(qrcodeToken, new LoginManager.QrcodeStatusCallback() {
                @Override
                public void onStatusChanged(QrcodeStatus status, QrcodeStatusResponse response) {
                    log.info("扫码状态：{}", status == null ? "未知" : status.getDescription());
                }

                @Override
                public void onScanned() {
                    log.info("二维码已扫描，等待确认");
                }

                @Override
                public void onSuccess(Credentials c) {
                    log.info("微信登录成功：{}", c.getUserId());
                }

                @Override
                public void onExpired() {
                    log.warn("二维码已过期");
                    state.set(State.DOWN);
                }
            });
            this.loggedInUserId = credentials.getUserId();
            startListening(candidate);
        } catch (Exception e) {
            log.error("扫码登录失败：{}", e.getMessage());
            lastError.set(e.getMessage());
            state.set(State.DOWN);
        } finally {
            this.pendingQrcode = null;
        }
    }

    private void startListening(WechatBotSdk candidate) {
        candidate.addMessageListener(new SimpleMessageListener() {
            @Override
            public void onMessagesReceived(List<WechatMessage> messages) {
                for (WechatMessage msg : messages) {
                    try {
                        // 收到消息时才解析 Handler，避开启动期的构造环
                        messageHandlerProvider.getObject().handle(msg);
                    } catch (Exception e) {
                        log.error("消息处理异常：{}", e.getMessage(), e);
                    }
                }
            }
        });
        candidate.startListening();
        this.sdk = candidate;
        this.state.set(State.CONNECTED);
        this.lastError.set(null);
        log.info("微信通道已连接");
    }

    /** 发送文本；未连接时抛出，由调用方决定降级。 */
    public boolean sendText(String toUserId, String content) throws Exception {
        WechatBotSdk current = this.sdk;
        if (current == null || !current.isAuthenticated()) {
            throw new IllegalStateException("微信通道未连接");
        }
        return current.sendTextMessage(toUserId, content);
    }

    /** 发送图片；未连接时抛出。 */
    public boolean sendImage(String toUserId, byte[] bytes) throws Exception {
        WechatBotSdk current = this.sdk;
        if (current == null || !current.isAuthenticated()) {
            throw new IllegalStateException("微信通道未连接");
        }
        return current.sendImage(toUserId, bytes);
    }

    /** 下载图片字节；未连接时抛出。 */
    public byte[] downloadImage(com.jnclub.wechat.ilink.model.message.ImageItem image) throws Exception {
        WechatBotSdk current = this.sdk;
        if (current == null || !current.isAuthenticated()) {
            throw new IllegalStateException("微信通道未连接");
        }
        return current.downloadImage(image);
    }

    public synchronized void stop() {
        WechatBotSdk current = this.sdk;
        if (current != null) {
            try {
                current.stopListening();
            } catch (Exception e) {
                log.warn("停止监听失败：{}", e.getMessage());
            }
        }
        this.state.set(State.DOWN);
        log.info("微信通道已停止");
    }

    private WechatBotSdk newSdk() {
        // 这是"凭据存放在哪一行"的键，与剪贴板归属用户无关——
        // 凭据是通道自身的，剪贴板数据才是 properties.userId 的。
        String userId = "wechat-channel";
        JdbcCredentialsStore credentialsStore = new JdbcCredentialsStore(jdbcTemplate, userId);
        JdbcBufferStore bufferStore = new JdbcBufferStore(jdbcTemplate, userId);
        ContextTokenCache.Persistence persistence = new ContextTokenCache.Persistence() {
            @Override
            public void save(String peerId, String contextToken) {
                contextTokenStore.put(userId, peerId, contextToken);
            }

            @Override
            public String load(String peerId) {
                return contextTokenStore.get(peerId);
            }
        };
        return new WechatBotSdk(credentialsStore, bufferStore, persistence);
    }
}
