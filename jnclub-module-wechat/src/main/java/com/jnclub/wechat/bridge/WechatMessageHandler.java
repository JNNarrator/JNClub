package com.jnclub.wechat.bridge;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.service.ClipboardSyncService;
import com.jnclub.wechat.config.WechatProperties;
import com.jnclub.wechat.ilink.media.MediaService;
import com.jnclub.wechat.ilink.model.message.MessageItem;
import com.jnclub.wechat.ilink.model.message.WechatMessage;
import com.jnclub.wechat.ilink.sender.MessageSenderService;
import com.jnclub.wechat.runtime.WechatChannelManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 处理一条来自微信的消息。
 *
 * <p>流程：白名单校验 → 指令解析 → 分支执行（写入 / 取回 / 搜索 / 帮助）。
 *
 * <p>所有失败都不向外抛：iLink 的轮询线程是单线程循环，一条消息处理抛异常
 * 会影响后续消息的接收，用户只会看到"发了没反应"，很难排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMessageHandler {

    private final WechatProperties properties;
    private final ClipboardBridgeService bridge;
    private final ClipboardSyncService clipboardSyncService;
    private final WechatChannelManager channelManager;
    private final MediaService mediaService;
    private final com.jnclub.clipboard.service.ClipboardBlobService clipboardBlobService;

    public void handle(WechatMessage message) {
        if (message == null || !message.isUserMessage()) {
            return;
        }
        String fromUserId = message.getFromUserId();

        if (!properties.isAllowed(fromUserId)) {
            log.warn("拒绝非白名单微信用户写入剪贴板：{}", fromUserId);
            safeSend(fromUserId, "⛔ 该微信账号未授权使用此通道。");
            return;
        }

        // 图片先于文本判断：图片消息的 item_list 里也可能带文字说明，
        // 但用户真正想同步的是那张图。
        MessageItem imageItem = firstImageItem(message);
        if (imageItem != null) {
            handleImage(message, fromUserId, imageItem);
            return;
        }

        String text = message.extractTextContent();
        if (text == null || text.isBlank()) {
            safeSend(fromUserId, "暂只支持文本与图片消息。");
            return;
        }

        WechatCommand command = WechatCommand.parse(text);
        log.info("微信指令 user={} kind={}", fromUserId, command.kind());

        try {
            switch (command.kind()) {
                case WRITE -> handleWrite(message, fromUserId, command.arg());
                case CLIP -> handleClip(fromUserId, 1);
                case CLIP_INDEX -> handleClip(fromUserId, Integer.parseInt(command.arg()));
                case SEARCH -> handleSearch(fromUserId, command.arg());
                case HELP -> safeSend(fromUserId, WechatCommand.helpText(List.of()));
            }
        } catch (Exception e) {
            log.error("处理微信消息失败：{}", e.getMessage(), e);
            safeSend(fromUserId, "处理失败：" + e.getMessage());
        }
    }

    /** 普通文本 → 写入电脑剪贴板。 */
    private void handleWrite(WechatMessage message, String fromUserId, String text) {
        boolean ok = bridge.pushText(
                properties.getUserId(),
                message.getClientId(),
                text,
                message.getCreateTimeMs());
        if (ok) {
            // 只回一个轻量确认：太多字会淹没用户真正想同步的内容。
            safeSend(fromUserId, "✅ 已同步到电脑剪贴板");
        }
    }

    /** 从消息里找第一个图片项。 */
    private static MessageItem firstImageItem(WechatMessage message) {
        if (message.getItemList() == null) {
            return null;
        }
        for (MessageItem item : message.getItemList()) {
            if (MediaService.isImage(item)) {
                return item;
            }
        }
        return null;
    }

    /** 手机发来的图片 → 下载解密 → 存入 dufs → 写入剪贴板。 */
    private void handleImage(WechatMessage message, String fromUserId, MessageItem imageItem) {
        try {
            byte[] bytes = mediaService.downloadImage(imageItem.getImageItem());
            long limit = com.jnclub.clipboard.service.ClipboardBlobService.MAX_BLOB_BYTES;
            if (bytes.length > limit) {
                // 微信原图常超过 20MB 的附件上限，先压缩再试——
                // 直接拒绝会让"发张照片到电脑"这件最自然的事失败。
                byte[] compressed = ImageCompressor.fitUnder(bytes, limit);
                if (compressed == null) {
                    safeSend(fromUserId, "❌ 图片过大（" + (bytes.length / 1024 / 1024)
                            + "MB）且无法压缩，未能同步。");
                    return;
                }
                bytes = compressed;
            }
            boolean ok = bridge.pushImage(
                    properties.getUserId(),
                    message.getClientId(),
                    bytes,
                    "image/png",
                    message.getCreateTimeMs());
            if (ok) {
                safeSend(fromUserId, "✅ 图片已同步到电脑剪贴板");
            }
        } catch (Exception e) {
            log.error("处理微信图片失败：{}", e.getMessage(), e);
            safeSend(fromUserId, "❌ 图片同步失败：" + e.getMessage());
        }
    }

    /** 电脑 → 手机：把最近的第 N 条发回去。 */
    private void handleClip(String toUserId, int index) {
        List<ClipboardRecordDto> recent = recentRecords(index);
        if (recent.size() < index) {
            safeSend(toUserId, "剪贴板里只有 " + recent.size() + " 条记录。");
            return;
        }
        ClipboardRecordDto dto = recent.get(index - 1);
        String body = dto.getContent();
        if (body == null || body.isBlank()) {
            safeSend(toUserId, "该条目没有可显示的内容（可能是文件路径）。");
            return;
        }
        // 图片的 content 是 sha256 引用，要按引用取回字节再发，
        // 直接把哈希发给用户没有意义。
        if ("image".equals(dto.getContentType())) {
            sendImageRecord(toUserId, dto, index);
            return;
        }
        safeSend(toUserId, String.format("📋 第 %d 条：%n%s", index, truncate(body, 1000)));
    }

    /** 按 sha256 引用取出图片并发给微信。 */
    private void sendImageRecord(String toUserId, ClipboardRecordDto dto, int index) {
        try {
            com.jnclub.clipboard.service.ClipboardBlobService.BlobPayload payload =
                    clipboardBlobService.load(properties.getUserId(), dto.getContent());
            if (payload == null || payload.bytes() == null || payload.bytes().length == 0) {
                safeSend(toUserId, "🖼️ 第 " + index + " 条是图片，但附件已不在服务端。");
                return;
            }
            safeSend(toUserId, "🖼️ 第 " + index + " 条：");
            channelManager.sendImage(toUserId, payload.bytes());
        } catch (Exception e) {
            log.warn("发送图片条目失败：{}", e.getMessage());
            safeSend(toUserId, "🖼️ 图片发送失败。");
        }
    }

    /** 电脑 → 手机：按关键词搜索。 */
    private void handleSearch(String toUserId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            safeSend(toUserId, "用法：/search 关键词");
            return;
        }
        List<ClipboardRecordDto> all = recentRecords(200);
        List<ClipboardRecordDto> hits = new ArrayList<>();
        for (ClipboardRecordDto dto : all) {
            String content = dto.getContent();
            if (content != null && content.toLowerCase().contains(keyword.toLowerCase())) {
                hits.add(dto);
                if (hits.size() >= 5) {
                    break;
                }
            }
        }
        if (hits.isEmpty()) {
            safeSend(toUserId, "没有找到包含「" + keyword + "」的记录。");
            return;
        }
        StringBuilder sb = new StringBuilder("🔍 找到 ").append(hits.size()).append(" 条：\n");
        for (int i = 0; i < hits.size(); i++) {
            sb.append(i + 1).append(". ").append(truncate(hits.get(i).getContent(), 80)).append("\n");
        }
        safeSend(toUserId, sb.toString().trim());
    }

    /**
     * 取最近的未删除记录。
     *
     * <p>用 pull(since=0) 拿全量再截断，是因为服务端没有"按时间倒序取 N 条"的接口。
     * 同一用户的历史规模在可接受范围内；数据量大时应改为服务端新增查询接口。
     */
    private List<ClipboardRecordDto> recentRecords(int limit) {
        SyncPullResponse response = clipboardSyncService.pull(properties.getUserId(), 0, ClipboardSyncService.MAX_PULL_LIMIT);
        List<ClipboardRecordDto> records = new ArrayList<>(response.getRecords());
        records.removeIf(r -> r.getDeletedAt() != null);
        records.sort(Comparator.comparing(
                (ClipboardRecordDto r) -> r.getSortAt() == null ? 0L : r.getSortAt()).reversed());
        return records.size() <= limit ? records : records.subList(0, limit);
    }

    private void safeSend(String toUserId, String content) {
        try {
            channelManager.sendText(toUserId, content);
        } catch (Exception e) {
            // 回不出去不影响主流程：可能是 context_token 过期，用户下次发消息会刷新它。
            log.warn("回复微信失败 to={}：{}", toUserId, e.getMessage());
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
