package com.jnclub.wechat.bridge;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.service.ClipboardBlobService;
import com.jnclub.clipboard.service.ClipboardSyncService;
import com.jnclub.wechat.config.WechatProperties;
import com.jnclub.wechat.config.WechatUserConfig;
import com.jnclub.wechat.runtime.WechatChannelManager;
import com.jnclub.wechat.runtime.WechatChannelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 电脑 → 手机：把各用户电脑端新复制的内容推到各自的微信。
 *
 * <h2>多租户下的轮询</h2>
 * 一个 {@code @Scheduled} 遍历所有已连接的通道，按<b>用户各自</b>的游标判断增量。
 * 用每用户游标而不是全局游标，是因为每个用户的 seq 空间独立。
 *
 * <h2>徊声抑制</h2>
 * 微信发来的消息本身也会写成剪贴板条目。按 {@code source_app} 过滤：
 * 来自 {@code wechat} 的条目不再推回微信，否则会形成"手机发一句 → 电脑收到 → 又推回手机"。
 */
@Slf4j
@Component
public class ClipboardStreamListener {

    /** 微信写入的条目标记，与 BridgeService 保持一致。 */
    private static final String WECHAT_SOURCE = "wechat";

    private final WechatProperties properties;
    private final WechatChannelRegistry registry;
    private final ClipboardSyncService clipboardSyncService;
    private final ClipboardBlobService clipboardBlobService;

    /** userId → 已推送到的 seq。 */
    private final Map<String, Long> lastSeq = new HashMap<>();
    private final Map<String, Boolean> warmedUp = new HashMap<>();

    public ClipboardStreamListener(WechatProperties properties,
                                   WechatChannelRegistry registry,
                                   ClipboardSyncService clipboardSyncService,
                                   ClipboardBlobService clipboardBlobService) {
        this.properties = properties;
        this.registry = registry;
        this.clipboardSyncService = clipboardSyncService;
        this.clipboardBlobService = clipboardBlobService;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 15000)
    public void poll() {
        for (WechatChannelManager channel : registry.connected()) {
            try {
                pollChannel(channel);
            } catch (Exception e) {
                log.debug("[{}] 剪贴板轮询失败：{}", channel.userId(), e.getMessage());
            }
        }
    }

    private synchronized void pollChannel(WechatChannelManager channel) {
        String userId = channel.userId();
        WechatUserConfig config = channel.config();
        if (!config.isNotifyOnCopy()) {
            return;
        }

        long current = clipboardSyncService.currentSeq(userId);
        if (!Boolean.TRUE.equals(warmedUp.get(userId))) {
            // 启动后第一拍只记录基线，不把历史记录全推一遍
            lastSeq.put(userId, current);
            warmedUp.put(userId, true);
            return;
        }
        long since = lastSeq.getOrDefault(userId, 0L);
        if (current <= since) {
            return;
        }
        lastSeq.put(userId, current);

        SyncPullResponse response = clipboardSyncService.pull(userId, since, 50);
        List<ClipboardRecordDto> fresh = new ArrayList<>();
        for (ClipboardRecordDto dto : response.getRecords()) {
            if (dto.getDeletedAt() != null) {
                continue;
            }
            if (WECHAT_SOURCE.equals(dto.getSourceApp())) {
                // 回声抑制：这条本来就是从微信来的
                continue;
            }
            String content = dto.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            fresh.add(dto);
        }
        if (fresh.isEmpty()) {
            return;
        }
        fresh.sort(Comparator.comparing(
                (ClipboardRecordDto d) -> d.getSortAt() == null ? 0L : d.getSortAt()).reversed());
        pushToWechat(channel, fresh);
    }

    private void pushToWechat(WechatChannelManager channel, List<ClipboardRecordDto> records) {
        List<String> peers = channel.knownPeers();
        if (peers.isEmpty()) {
            return;
        }
        ClipboardRecordDto latest = records.get(0);

        for (String peer : peers) {
            try {
                if ("image".equals(latest.getContentType())) {
                    sendImageTo(channel, peer, latest);
                } else {
                    String content = latest.getContent();
                    String preview = content.length() <= properties.getNotifyPreviewLength()
                            ? content
                            : content.substring(0, properties.getNotifyPreviewLength()) + "…";
                    String message = "📋 电脑已复制：" + preview
                            + (records.size() > 1 ? "\n（另有 " + (records.size() - 1) + " 条）" : "");
                    channel.sendText(peer, message);
                }
            } catch (Exception e) {
                // context_token 过期是常态，静默降级：用户下次发消息会自动刷新。
                log.debug("[{}] 推送到 {} 失败：{}", channel.userId(), peer, e.getMessage());
            }
        }
    }

    /**
     * 把一条图片记录推给微信。
     *
     * <p>记录里的 content 是 sha256，字节在 dufs：先按引用取回，再交给通道上传 CDN。
     * 取不到时降级成文字说明，避免静默失败。
     */
    private void sendImageTo(WechatChannelManager channel, String peer, ClipboardRecordDto dto) {
        String sha = dto.getContent();
        try {
            ClipboardBlobService.BlobPayload payload =
                    clipboardBlobService.load(channel.userId(), sha);
            if (payload == null || payload.bytes() == null || payload.bytes().length == 0) {
                channel.sendText(peer, "🖼️ 电脑复制了一张图片，但附件已不在服务端。");
                return;
            }
            channel.sendImage(peer, payload.bytes());
        } catch (Exception e) {
            log.debug("[{}] 图片推送到 {} 失败：{}", channel.userId(), peer, e.getMessage());
            try {
                channel.sendText(peer, "🖼️ 电脑复制了一张图片，同步失败。");
            } catch (Exception ignored) {
                // 连文字也发不出去，说明 context_token 已失效
            }
        }
    }
}
