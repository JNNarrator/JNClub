package com.jnclub.wechat.runtime;

import com.jnclub.wechat.bridge.WechatMessageHandler;
import com.jnclub.wechat.config.WechatUserConfig;
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
import com.jnclub.wechat.persistence.WechatConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <b>一个 JNClub 用户</b>的微信通道：扫码登录、启停监听、发送消息。
 *
 * <h2>为什么是每用户一个实例，而不是单例</h2>
 * iLink 的 bot 就是"扫码登录的那个微信号"。两个 JNClub 账号若共用一个实例，
 * 就只能共用一个 bot、一份凭据、一个 context_token 集合，消息必然串到同一处。
 * 所以每个账号各自扫码、各自持有一份 SDK 实例，互不干扰。
 *
 * <p>本类不再注册为 Spring Bean，由 {@link WechatChannelRegistry} 按需创建。
 *
 * <p>状态机：{@code DOWN → WAITING_SCAN → CONNECTED}，任一步失败回到 DOWN。
 */
@Slf4j
public class WechatChannelManager {

    public enum State { DOWN, WAITING_SCAN, CONNECTED }

    private final String userId;
    private final JdbcTemplate jdbcTemplate;
    private final ContextTokenStore contextTokenStore;
    private final WechatConfigStore configStore;
    private final ObjectProvider<WechatMessageHandler> messageHandlerProvider;

    private final AtomicReference<State> state = new AtomicReference<>(State.DOWN);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private volatile WechatBotSdk sdk;
    private volatile String loggedInWechatUserId;
    private volatile String pendingQrcode;

    public WechatChannelManager(String userId,
                                JdbcTemplate jdbcTemplate,
                                ContextTokenStore contextTokenStore,
                                WechatConfigStore configStore,
                                ObjectProvider<WechatMessageHandler> messageHandlerProvider) {
        this.userId = userId;
        this.jdbcTemplate = jdbcTemplate;
        this.contextTokenStore = contextTokenStore;
        this.configStore = configStore;
        this.messageHandlerProvider = messageHandlerProvider;
    }

    public String userId() {
        return userId;
    }

    public State state() {
        return state.get();
    }

    public String lastError() {
        return lastError.get();
    }

    public String loggedInWechatUserId() {
        return loggedInWechatUserId;
    }

    public boolean isConnected() {
        return state.get() == State.CONNECTED && sdk != null;
    }

    public String pendingQrcode() {
        return pendingQrcode;
    }

    /** 当前用户配置（每次读库，量小且保证与设置页的改动一致）。 */
    public WechatUserConfig config() {
        return configStore.load(userId);
    }

    /**
     * 用已保存的凭据恢复通道。
     */
    public synchronized boolean resume() {
        try {
            WechatBotSdk candidate = newSdk();
            if (!candidate.isAuthenticated()) {
                log.debug("[{}] 没有可用的微信凭据，需要扫码登录", userId);
                return false;
            }
            startListening(candidate);
            return true;
        } catch (Exception e) {
            log.warn("[{}] 恢复微信通道失败：{}", userId, e.getMessage());
            lastError.set(e.getMessage());
            return false;
        }
    }

    /** 请求登录二维码；已登录则返回 null。 */
    public synchronized String requestQrcode() throws Exception {
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
        Thread.ofVirtual().name("wechat-login-" + userId).start(() -> awaitScan(candidate, qrcode.getQrcode()));
        return qrcode.getQrcodeImgContent();
    }

    private void awaitScan(WechatBotSdk candidate, String qrcodeToken) {
        try {
            Credentials credentials = candidate.loginWithQrcode(qrcodeToken, new LoginManager.QrcodeStatusCallback() {
                @Override
                public void onStatusChanged(QrcodeStatus status, QrcodeStatusResponse response) {
                    log.info("[{}] 扫码状态：{}", userId, status == null ? "未知" : status.getDescription());
                }

                @Override
                public void onScanned() {
                    log.info("[{}] 二维码已扫描，等待确认", userId);
                }

                @Override
                public void onSuccess(Credentials c) {
                    log.info("[{}] 微信登录成功：{}", userId, c.getUserId());
                }

                @Override
                public void onExpired() {
                    log.warn("[{}] 二维码已过期", userId);
                    state.set(State.DOWN);
                }
            });
            this.loggedInWechatUserId = credentials.getUserId();
            // bot 属于登录者本人，所以登录后自动把本人加进白名单：
            // 这样通道开箱即用，又不会因为白名单为空而对陌生人敞开。
            configStore.addWhitelistEntry(userId, credentials.getUserId());
            startListening(candidate);
        } catch (Exception e) {
            log.error("[{}] 扫码登录失败：{}", userId, e.getMessage());
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
                        messageHandlerProvider.getObject().handle(WechatChannelManager.this, msg);
                    } catch (Exception e) {
                        log.error("[{}] 消息处理异常：{}", userId, e.getMessage(), e);
                    }
                }
            }
        });
        candidate.startListening();
        this.sdk = candidate;
        this.state.set(State.CONNECTED);
        this.lastError.set(null);
        log.info("[{}] 微信通道已连接", userId);
    }

    public boolean sendText(String toUserId, String content) throws Exception {
        WechatBotSdk current = this.sdk;
        if (current == null || !current.isAuthenticated()) {
            throw new IllegalStateException("微信通道未连接");
        }
        return current.sendTextMessage(toUserId, content);
    }

    public boolean sendImage(String toUserId, byte[] bytes) throws Exception {
        WechatBotSdk current = this.sdk;
        if (current == null || !current.isAuthenticated()) {
            throw new IllegalStateException("微信通道未连接");
        }
        return current.sendImage(toUserId, bytes);
    }

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
                log.warn("[{}] 停止监听失败：{}", userId, e.getMessage());
            }
        }
        this.state.set(State.DOWN);
        log.info("[{}] 微信通道已停止", userId);
    }

    /** 已缓存的对话方，用于主动推送。 */
    public List<String> knownPeers() {
        return contextTokenStore.peersOf(userId);
    }

    private WechatBotSdk newSdk() {
        JdbcCredentialsStore credentialsStore = new JdbcCredentialsStore(jdbcTemplate, userId);
        JdbcBufferStore bufferStore = new JdbcBufferStore(jdbcTemplate, userId);
        // 凭据与 context_token 都按本用户隔离：两个账号各扫各的码，互不可见。
        ContextTokenCache.Persistence persistence = new ContextTokenCache.Persistence() {
            @Override
            public void save(String peerId, String contextToken) {
                contextTokenStore.put(userId, peerId, contextToken);
            }

            @Override
            public String load(String peerId) {
                // 必须带 userId：同一个微信用户可能分别给两个账号的 bot 发过消息，
                // 各自的 context_token 不同。
                return contextTokenStore.get(userId, peerId);
            }
        };
        return new WechatBotSdk(credentialsStore, bufferStore, persistence);
    }
}
