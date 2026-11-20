package com.jnclub.wechat.bridge;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.service.ClipboardBlobService;
import com.jnclub.clipboard.service.ClipboardSyncService;
import com.jnclub.wechat.config.WechatUserConfig;
import com.jnclub.wechat.ilink.model.message.ImageItem;
import com.jnclub.wechat.ilink.model.message.MessageItem;
import com.jnclub.wechat.ilink.model.message.MessageType;
import com.jnclub.wechat.ilink.model.message.WechatMessage;
import com.jnclub.wechat.persistence.WechatConfigStore;
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
 * <p>多租户后这里不再持有"那个"通道，而是由调用方传入：
 * 消息归属哪个用户，由收消息的那个 {@link WechatChannelManager} 决定。
 * 所有读写都按 {@code channel.userId()} 隔离。
 *
 * <p>所有失败都不向外抛：iLink 的轮询线程是单线程循环，一条消息处理抛异常
 * 会影响后续消息的接收，用户只会看到"发了没反应"，很难排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMessageHandler {

    private final ClipboardBridgeService bridge;
    private final ClipboardSyncService clipboardSyncService;
    private final ClipboardBlobService clipboardBlobService;
    private final WechatConfigStore configStore;

    public void handle(WechatChannelManager channel, WechatMessage message) {
        if (channel == null || message == null || !message.isUserMessage()) {
            return;
        }
        String userId = channel.userId();
        String fromUserId = message.getFromUserId();
        WechatUserConfig config = channel.config();

        if (!isAllowed(config, fromUserId)) {
            log.warn("[{}] 拒绝非白名单微信用户写入剪贴板：{}", userId, fromUserId);
            safeSend(channel, fromUserId, "⛔ 该微信账号未授权使用此通道。");
            return;
        }

        // 图片先于文本判断：图片消息也可能带文字说明，但用户真正想同步的是那张图
        MessageItem imageItem = firstImageItem(message);
        if (imageItem != null) {
            handleImage(channel, message, fromUserId, imageItem);
            return;
        }

        String text = message.extractTextContent();
        if (text == null || text.isBlank()) {
            safeSend(channel, fromUserId, "暂只支持文本与图片消息。");
            return;
        }

        WechatCommand command = WechatCommand.parse(text);
        log.info("[{}] 微信指令 user={} kind={}", userId, fromUserId, command.kind());

        try {
            switch (command.kind()) {
                case WRITE -> handleWrite(channel, message, fromUserId, command.arg());
                case CLIP -> handleClip(channel, fromUserId, 1);
                case CLIP_INDEX -> handleClip(channel, fromUserId, Integer.parseInt(command.arg()));
                case SEARCH -> handleSearch(channel, fromUserId, command.arg());
                case PIN -> handlePin(channel, fromUserId, Integer.parseInt(command.arg()), true);
                case UNPIN -> handlePin(channel, fromUserId, Integer.parseInt(command.arg()), false);
                case DELETE -> handleDelete(channel, fromUserId, Integer.parseInt(command.arg()));
                case HELP -> safeSend(channel, fromUserId, WechatCommand.helpText(List.of()));
            }
        } catch (Exception e) {
            log.error("[{}] 处理微信消息失败：{}", userId, e.getMessage(), e);
            safeSend(channel, fromUserId, "处理失败：" + e.getMessage());
        }
    }

    /**
     * 准入判断：白名单为空表示不允许任何人。
     *
     * <p>多租户下不能再"空即放行"：每个 bot 都是用户自己的微信号，
     * 陌生人给它发消息会把内容写进主人的剪贴板。用户扫码后系统会自动
     * 把本人加进白名单，所以正常情况下不会为空。
     */
    static boolean isAllowed(WechatUserConfig config, String fromUserId) {
        if (fromUserId == null || fromUserId.isBlank()) {
            return false;
        }
        List<String> entries = WechatConfigStore.splitWhitelist(config.getWhitelist());
        if (entries.isEmpty()) {
            return false;
        }
        String normalized = fromUserId.trim().toLowerCase();
        for (String entry : entries) {
            if (entry.trim().toLowerCase().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** 判断一项是不是图片：type=IMAGE 且 image_item 不为空。 */
    private static boolean isImageItem(MessageItem item) {
        return item != null
                && item.getType() != null
                && item.getType() == MessageType.Item.IMAGE
                && item.getImageItem() != null;
    }

    /** 从消息里找第一个图片项。 */
    private static MessageItem firstImageItem(WechatMessage message) {
        if (message.getItemList() == null) {
            return null;
        }
        for (MessageItem item : message.getItemList()) {
            if (isImageItem(item)) {
                return item;
            }
        }
        return null;
    }

    /** 手机发来的图片 → 下载解密 → 存入 dufs → 写入剪贴板。 */
    private void handleImage(WechatChannelManager channel, WechatMessage message,
                             String fromUserId, MessageItem imageItem) {
        try {
            ImageItem image = imageItem.getImageItem();
            byte[] bytes = channel.downloadImage(image);
            long limit = ClipboardBlobService.MAX_BLOB_BYTES;
            if (bytes.length > limit) {
                // 微信原图常超过 20MB 的附件上限，先压缩再试
                byte[] compressed = ImageCompressor.fitUnder(bytes, limit);
                if (compressed == null) {
                    safeSend(channel, fromUserId, "❌ 图片过大（" + (bytes.length / 1024 / 1024)
                            + "MB）且无法压缩，未能同步。");
                    return;
                }
                bytes = compressed;
            }
            boolean ok = bridge.pushImage(
                    channel.userId(),
                    message.getClientId(),
                    bytes,
                    "image/png",
                    message.getCreateTimeMs());
            if (ok) {
                safeSend(channel, fromUserId, "✅ 图片已同步到电脑剪贴板");
            }
        } catch (Exception e) {
            log.error("[{}] 处理微信图片失败：{}", channel.userId(), e.getMessage(), e);
            safeSend(channel, fromUserId, "❌ 图片同步失败：" + e.getMessage());
        }
    }

    /** 普通文本 → 写入电脑剪贴板。 */
    private void handleWrite(WechatChannelManager channel, WechatMessage message,
                             String fromUserId, String text) {
        boolean ok = bridge.pushText(
                channel.userId(),
                message.getClientId(),
                text,
                message.getCreateTimeMs());
        if (!ok) {
            return;
        }
        // 超长文本在微信侧就已经被截断了，用户看到的长度与实际到达的可能不同。
        // 明确提示一下，避免"我明明发了更多"的困惑。
        if (text.length() >= TextChunker.DEFAULT_CHUNK_CHARS) {
            safeSend(channel, fromUserId,
                    "✅ 已同步到电脑剪贴板（" + text.length() + " 字）。"
                            + "若原文更长，微信可能已截断，建议长内容用网页版。");
        } else {
            safeSend(channel, fromUserId, "✅ 已同步到电脑剪贴板");
        }
    }

    /** 电脑 → 手机：把最近的第 N 条发回去。 */
    private void handleClip(WechatChannelManager channel, String toUserId, int index) {
        List<ClipboardRecordDto> recent = recentRecords(channel.userId(), index);
        if (recent.size() < index) {
            safeSend(channel, toUserId, "剪贴板里只有 " + recent.size() + " 条记录。");
            return;
        }
        ClipboardRecordDto dto = recent.get(index - 1);
        String body = dto.getContent();
        if (body == null || body.isBlank()) {
            safeSend(channel, toUserId, "该条目没有可显示的内容（可能是文件路径）。");
            return;
        }
        // 图片的 content 是 sha256 引用，要按引用取回字节再发
        if ("image".equals(dto.getContentType())) {
            sendImageRecord(channel, toUserId, dto, index);
            return;
        }
        safeSend(channel, toUserId, String.format("📋 第 %d 条：%n%s", index, truncate(body, 1000)));
    }

    /** 按 sha256 引用取出图片并发给微信。 */
    private void sendImageRecord(WechatChannelManager channel, String toUserId,
                                 ClipboardRecordDto dto, int index) {
        try {
            ClipboardBlobService.BlobPayload payload =
                    clipboardBlobService.load(channel.userId(), dto.getContent());
            if (payload == null || payload.bytes() == null || payload.bytes().length == 0) {
                safeSend(channel, toUserId, "🖼️ 第 " + index + " 条是图片，但附件已不在服务端。");
                return;
            }
            safeSend(channel, toUserId, "🖼️ 第 " + index + " 条：");
            channel.sendImage(toUserId, payload.bytes());
        } catch (Exception e) {
            log.warn("[{}] 发送图片条目失败：{}", channel.userId(), e.getMessage());
            safeSend(channel, toUserId, "🖼️ 图片发送失败。");
        }
    }

    /**
     * 置顶 / 取消置顶。
     *
     * <p>只置顶不动正文：置顶是排序上的标记，不应该改变条目内容。
     */
    private void handlePin(WechatChannelManager channel, String toUserId, int index, boolean pinned) {
        List<ClipboardRecordDto> recent = recentRecords(channel.userId(), index);
        if (recent.size() < index) {
            safeSend(channel, toUserId, "剪贴板里只有 " + recent.size() + " 条记录。");
            return;
        }
        ClipboardRecordDto target = recent.get(index - 1);
        boolean ok = bridge.setPinned(channel.userId(), target, pinned);
        if (!ok) {
            safeSend(channel, toUserId, "❌ 操作失败，可能这条刚被其它设备改过，请先发 /clip " + index + " 看最新内容。");
            return;
        }
        String verb = pinned ? "置顶" : "取消置顶";
        safeSend(channel, toUserId, "📌 第 " + index + " 条已" + verb + "：" + truncate(target.getContent(), 60));
    }

    /**
     * 删除一条记录。
     *
     * <p>删除是不可逆的（服务端用墓碑，不做回收站），所以先把要删的内容回显给用户，
     * 让"我删的是不是这条"这件事在动手之后至少可核对。
     */
    private void handleDelete(WechatChannelManager channel, String toUserId, int index) {
        List<ClipboardRecordDto> recent = recentRecords(channel.userId(), index);
        if (recent.size() < index) {
            safeSend(channel, toUserId, "剪贴板里只有 " + recent.size() + " 条记录。");
            return;
        }
        ClipboardRecordDto target = recent.get(index - 1);
        boolean ok = bridge.delete(channel.userId(), target);
        if (!ok) {
            safeSend(channel, toUserId, "❌ 删除失败，可能这条刚被其它设备改过，请重试。");
            return;
        }
        safeSend(channel, toUserId, "🗑️ 已删除第 " + index + " 条：" + truncate(target.getContent(), 60));
    }

    /** 电脑 → 手机：按关键词搜索。 */
    private void handleSearch(WechatChannelManager channel, String toUserId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            safeSend(channel, toUserId, "用法：/search 关键词");
            return;
        }
        List<ClipboardRecordDto> all = recentRecords(channel.userId(), 200);
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
            safeSend(channel, toUserId, "没有找到包含「" + keyword + "」的记录。");
            return;
        }
        StringBuilder sb = new StringBuilder("🔍 找到 ").append(hits.size()).append(" 条：\n");
        for (int i = 0; i < hits.size(); i++) {
            sb.append(i + 1).append(". ").append(truncate(hits.get(i).getContent(), 80)).append("\n");
        }
        safeSend(channel, toUserId, sb.toString().trim());
    }

    private List<ClipboardRecordDto> recentRecords(String userId, int limit) {
        SyncPullResponse response = clipboardSyncService.pull(
                userId, 0, ClipboardSyncService.MAX_PULL_LIMIT);
        List<ClipboardRecordDto> records = new ArrayList<>(response.getRecords());
        records.removeIf(r -> r.getDeletedAt() != null);
        records.sort(Comparator.comparing(
                (ClipboardRecordDto r) -> r.getSortAt() == null ? 0L : r.getSortAt()).reversed());
        return records.size() <= limit ? records : records.subList(0, limit);
    }

    private void safeSend(WechatChannelManager channel, String toUserId, String content) {
        try {
            channel.sendText(toUserId, content);
        } catch (Exception e) {
            // 回不出去不影响主流程：可能是 context_token 过期，用户下次发消息会刷新它。
            log.warn("[{}] 回复微信失败 to={}：{}", channel.userId(), toUserId, e.getMessage());
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
