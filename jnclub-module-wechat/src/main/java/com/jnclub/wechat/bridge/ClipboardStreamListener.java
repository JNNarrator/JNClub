package com.jnclub.wechat.bridge;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.service.ClipboardBlobService;
import com.jnclub.clipboard.service.ClipboardSyncService;
import com.jnclub.wechat.config.WechatProperties;
import com.jnclub.wechat.runtime.WechatChannelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 电脑 → 手机：把电脑端新复制的内容推到微信。
 *
 * <h2>为什么用轮询而不是去改剪贴板模块加事件钩子</h2>
 * 剪贴板模块的 push 落库后会 {@code streamService.notifyUser} 唤醒 SSE，
 * 但那条广播是给"其它设备"用的，微信通道不在其中，也不该为一处需求
 * 去改动一个正在服役的同步模块。
 *
 * <p>这里用 {@code /sync/state} 的语义做增量探测：记一个 {@code lastNotifiedSeq}
 * 游标，每 3 秒问一次当前 seq，只有前进了才 pull 增量。代价是空探测开销，
 * 收益是<b>零侵入</b>——剪贴板模块一行代码都不用动，也不会引入两个模块的循环依赖。
 *
 * <h2>回声抑制</h2>
 * 微信发来的消息本身也会写成剪贴板条目。如果不加判断，就会出现
 * "手机发一句 → 电脑收到 → 又被推回手机"的循环。
 * 这里按 {@code source_app} 过滤：来自 {@code wechat} 的条目不再推回微信。
 */
@Slf4j
@Component
public class ClipboardStreamListener {

    /** 微信写入的条目标记，与 BridgeService 保持一致。 */
    private static final String WECHAT_SOURCE = "wechat";

    private final WechatProperties properties;
    private final WechatChannelManager channelManager;
    private final ClipboardSyncService clipboardSyncService;
    private final ClipboardBlobService clipboardBlobService;
    private final JdbcTemplate jdbcTemplate;

    private volatile long lastNotifiedSeq = 0;
    private volatile boolean warmedUp = false;

    public ClipboardStreamListener(WechatProperties properties,
                                   WechatChannelManager channelManager,
                                   ClipboardSyncService clipboardSyncService,
                                   ClipboardBlobService clipboardBlobService,
                                   JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.channelManager = channelManager;
        this.clipboardSyncService = clipboardSyncService;
        this.clipboardBlobService = clipboardBlobService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 每 3 秒探测一次；只有 seq 前进才真正拉取与推送。 */
    @Scheduled(fixedDelay = 3000, initialDelay = 15000)
    public void poll() {
        if (!properties.isNotifyOnCopy() || !channelManager.isConnected()) {
            return;
        }
        try {
            long current = clipboardSyncService.currentSeq(properties.getUserId());
            if (!warmedUp) {
                // 启动后第一拍只记录基线，不把历史记录全推一遍。
                lastNotifiedSeq = current;
                warmedUp = true;
                return;
            }
            if (current <= lastNotifiedSeq) {
                return;
            }
            long since = lastNotifiedSeq;
            lastNotifiedSeq = current;

            SyncPullResponse response = clipboardSyncService.pull(properties.getUserId(), since, 50);
            List<ClipboardRecordDto> fresh = new ArrayList<>();
            for (ClipboardRecordDto dto : response.getRecords()) {
                if (dto.getDeletedAt() != null) {
                    continue;
                }
                if (WECHAT_SOURCE.equals(dto.getSourceApp())) {
                    // 回声抑制：这条本来就是从微信来的，不要再推回去。
                    continue;
                }
                // 图片的 content 是 blob 的 sha256，不是正文，但同样要推。
                boolean isImage = "image".equals(dto.getContentType());
                String content = dto.getContent();
                if (!isImage && (content == null || content.isBlank())) {
                    continue;
                }
                if (isImage && (content == null || content.isBlank())) {
                    continue;
                }
                fresh.add(dto);
            }
            if (fresh.isEmpty()) {
                return;
            }
            fresh.sort(Comparator.comparing(
                    (ClipboardRecordDto d) -> d.getSortAt() == null ? 0L : d.getSortAt()).reversed());
            pushToWechat(fresh);
        } catch (Exception e) {
            log.debug("剪贴板轮询失败：{}", e.getMessage());
        }
    }

    /**
     * 把一条图片记录推给微信。
     *
     * <p>记录里的 content 是 sha256，字节在 dufs：先按引用取回，再交给通道上传 CDN。
     * 取不到（已被 GC 或从未上传）时降级成一条文字说明，
     * 而不是静默失败——用户至少知道"这里有个图，但没同步过去"。
     */
    private void sendImageTo(String peer, ClipboardRecordDto dto) {
        String sha = dto.getContent();
        try {
            ClipboardBlobService.BlobPayload payload =
                    clipboardBlobService.load(properties.getUserId(), sha);
            if (payload == null || payload.bytes() == null || payload.bytes().length == 0) {
                channelManager.sendText(peer, "🖼️ 电脑复制了一张图片，但附件已不在服务端。");
                return;
            }
            channelManager.sendImage(peer, payload.bytes());
        } catch (Exception e) {
            log.debug("图片推送到 {} 失败：{}", peer, e.getMessage());
            try {
                channelManager.sendText(peer, "🖼️ 电脑复制了一张图片，同步失败。");
            } catch (Exception ignored) {
                // 连文字也发不出去，说明 context_token 已失效，交给下次刷新。
            }
        }
    }

    private void pushToWechat(List<ClipboardRecordDto> records) {
        List<Map<String, Object>> peers = jdbcTemplate.queryForList(
                "SELECT peer_id FROM t_wechat_context_token WHERE user_id = ?", properties.getUserId());
        if (peers.isEmpty()) {
            return;
        }
        ClipboardRecordDto latest = records.get(0);

        for (Map<String, Object> row : peers) {
            String peer = String.valueOf(row.get("peer_id"));
            try {
                if ("image".equals(latest.getContentType())) {
                    sendImageTo(peer, latest);
                } else {
                    String content = latest.getContent();
                    String preview = content.length() <= properties.getNotifyPreviewLength()
                            ? content
                            : content.substring(0, properties.getNotifyPreviewLength()) + "…";
                    String message = "📋 电脑已复制：" + preview
                            + (records.size() > 1 ? "\n（另有 " + (records.size() - 1) + " 条）" : "");
                    channelManager.sendText(peer, message);
                }
            } catch (Exception e) {
                // context_token 过期是常态，静默降级：用户下次发消息会自动刷新。
                log.debug("推送到 {} 失败：{}", peer, e.getMessage());
            }
        }
    }
}
