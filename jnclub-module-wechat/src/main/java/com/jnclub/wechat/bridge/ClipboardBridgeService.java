package com.jnclub.wechat.bridge;

import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.SyncPushRequest;
import com.jnclub.clipboard.dto.SyncPushResponse;
import com.jnclub.clipboard.service.ClipboardBlobService;
import com.jnclub.clipboard.service.ClipboardSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * 微信消息 → 剪贴板同步引擎的写入桥。
 *
 * <h2>为什么直接复用 ClipboardSyncService 而不是新开接口</h2>
 * 剪贴板模块已经实现了完整的同步语义：服务端分配 seq、rev 乐观锁、
 * syncId 幂等、7 天窗口内按内容去重、墓碑删除赢过编辑。
 * 微信通道唯一需要的动作就是"把一条文本当成某台设备的写入提交上去"，
 * 而 {@code push} 恰好就是这个动作。直接调用它，可以同时拿到两件事：
 * <ul>
 *   <li>所有剪贴板侧的规则（去重、版本、游标）自动生效，不必重复实现；</li>
 *   <li>{@code ClipboardController.push} 落库后回调 {@code streamService.notifyUser}，
 *       于是电脑端的长连接被立刻唤醒——实时性是白送的，不需要额外推送机制。</li>
 * </ul>
 *
 * <h2>幂等</h2>
 * 幂等键用 iLink 消息自带的 {@code client_id}，而不是内容哈希：
 * 用户连续两次发"1"，内容相同但它们是两条独立的剪贴记录，
 * 用内容哈希会把第二条吞掉。client_id 由发送方生成，天然唯一。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipboardBridgeService {

    /** 微信来源在剪贴板里的标记，便于在客户端按来源筛选。 */
    public static final String SOURCE_APP = "wechat";

    private final ClipboardSyncService clipboardSyncService;
    private final ClipboardBlobService clipboardBlobService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 把一条来自微信的消息写入剪贴板。
     *
     * @param userId     JNClub 用户标识（同步引擎按它隔离数据）
     * @param clientId   iLink 消息幂等键
     * @param text       正文
     * @param createTimeMs 消息时间（毫秒），用于排序
     * @return 是否真的写入了（重复消息返回 false）
     */
    public boolean pushText(String userId, String clientId, String text, Long createTimeMs) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (!markSeen(userId, clientId)) {
            log.debug("微信消息 {} 已处理过，跳过", clientId);
            return false;
        }

        ClipboardRecordDto dto = new ClipboardRecordDto();
        // syncId 由 clientId 稳定推导：同一条微信消息在任何时候重放都会得到同一个 syncId，
        // 于是服务端的 syncId 幂等层再兜一次。
        dto.setSyncId(syncIdOf(clientId));
        dto.setContentType("text");
        dto.setContent(text);
        dto.setContentHash(hashOf(text));
        dto.setPreview(preview(text));
        dto.setSourceApp(SOURCE_APP);
        dto.setIsPinned(false);
        dto.setTags(List.of());
        long now = createTimeMs != null && createTimeMs > 0 ? createTimeMs : System.currentTimeMillis();
        dto.setCreatedAt(now);
        dto.setLastUsedAt(now);
        dto.setSortAt(now);
        dto.setUseCount(1);
        dto.setRev(1L);

        SyncPushRequest request = new SyncPushRequest();
        request.setRecords(List.of(dto));
        request.setSettings(List.of());
        request.setBlobs(List.of());

        try {
            SyncPushResponse response = clipboardSyncService.push(userId, request);
            log.info("微信 → 剪贴板：user={} accepted={} conflicts={}",
                    userId, response.getAccepted().size(), response.getConflicts().size());
            return true;
        } catch (Exception e) {
            log.error("写入剪贴板失败：{}", e.getMessage(), e);
            // 写入失败则撤销幂等标记，让下一次重试有机会成功，
            // 否则一条因数据库抖动失败的消息会被永久丢弃。
            unmarkSeen(clientId);
            return false;
        }
    }

    /**
     * 把一张图片写入剪贴板。
     *
     * <p>图片与文本的关键差别：正文不是内容本身，而是 {@code blob 的 sha256}。
     * 字节存进 dufs，记录里只放引用——这样同一张图在多条记录里只存一份，
     * 也不会把几 MB 的 base64 塞进同步协议的 JSON。
     *
     * <p>sha256 必须用 {@code clipboardBlobService.store()} 返回的那个值，
     * 而不是自己另算一份：store 是幂等的（已存在则复用），
     * 它返回什么就代表 dufs 里实际存的是哪份内容。
     */
    public boolean pushImage(String userId, String clientId, byte[] imageBytes, String mime, Long createTimeMs) {
        if (imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        if (imageBytes.length > ClipboardBlobService.MAX_BLOB_BYTES) {
            log.warn("图片超过上限 {}B，拒绝写入：{}B", ClipboardBlobService.MAX_BLOB_BYTES, imageBytes.length);
            return false;
        }
        if (!markSeen(userId, clientId)) {
            log.debug("微信图片 {} 已处理过，跳过", clientId);
            return false;
        }

        String sha;
        try {
            sha = clipboardBlobService.store(userId, imageBytes, mime == null ? "image/png" : mime);
        } catch (Exception e) {
            log.error("图片存入 dufs 失败：{}", e.getMessage(), e);
            unmarkSeen(clientId);
            return false;
        }

        ClipboardRecordDto dto = new ClipboardRecordDto();
        dto.setSyncId(syncIdOf(clientId));
        dto.setContentType("image");
        // image 类型的 content 是 blob 的 sha256 引用，不是字节
        dto.setContent(sha);
        dto.setContentHash(hashOf(sha));
        dto.setPreview("[图片]");
        dto.setSourceApp(SOURCE_APP);
        dto.setIsPinned(false);
        dto.setTags(List.of());
        long now = createTimeMs != null && createTimeMs > 0 ? createTimeMs : System.currentTimeMillis();
        dto.setCreatedAt(now);
        dto.setLastUsedAt(now);
        dto.setSortAt(now);
        dto.setUseCount(1);
        dto.setRev(1L);

        SyncPushRequest request = new SyncPushRequest();
        request.setRecords(List.of(dto));
        request.setSettings(List.of());
        // 引用计数要在 push 时一起报上去，否则 blob 会被 GC 掉
        request.setBlobs(List.of(blobRef(sha, imageBytes.length, mime)));

        try {
            SyncPushResponse response = clipboardSyncService.push(userId, request);
            log.info("微信图片 → 剪贴板：user={} sha={} accepted={}",
                    userId, sha, response.getAccepted().size());
            return true;
        } catch (Exception e) {
            log.error("图片写入剪贴板失败：{}", e.getMessage(), e);
            unmarkSeen(clientId);
            return false;
        }
    }

    private static com.jnclub.clipboard.dto.BlobRefDto blobRef(String sha, int size, String mime) {
        com.jnclub.clipboard.dto.BlobRefDto ref = new com.jnclub.clipboard.dto.BlobRefDto();
        ref.setSha256(sha);
        ref.setByteSize((long) size);
        ref.setMime(mime == null ? "image/png" : mime);
        return ref;
    }

    /**
     * 置顶 / 取消置顶一条已有记录。
     *
     * <p>走 push 而不是新开接口：服务端没有"改置顶"的专用端点，
     * 但 push 的语义就是"以本设备身份提交一条变更"，带 rev 做乐观锁。
     * 复用它可以自动获得冲突处理，不必在通道层重新实现。
     *
     * @param record 目标记录（pull 拿到的完整 DTO）
     * @param pinned 是否置顶
     */
    public boolean setPinned(String userId, ClipboardRecordDto record, boolean pinned) {
        ClipboardRecordDto dto = copyForUpdate(record);
        dto.setIsPinned(pinned);
        dto.setPinnedOrder(pinned ? (int) (System.currentTimeMillis() / 1000) : null);
        return pushUpdate(userId, dto, pinned ? "置顶" : "取消置顶");
    }

    /**
     * 删除一条记录。
     *
     * <p>用墓碑而不是物理删除：服务端靠 deletedAt 把"删除"同步给其它设备，
     * 直接删行会让对端在下一次 pull 时把它当成"没变过"而保留旧副本。
     * 这也是剪贴板模块既有的"删除赢过并发编辑"规则所依赖的形态。
     */
    public boolean delete(String userId, ClipboardRecordDto record) {
        ClipboardRecordDto dto = copyForUpdate(record);
        dto.setDeletedAt(System.currentTimeMillis());
        return pushUpdate(userId, dto, "删除");
    }

    /**
     * 复制一份用于更新的 DTO。
     *
     * <p>不直接改传进来的对象：它是 pull 结果的一部分，被别处引用着，
     * 就地修改会让调用方看到一份"已经不是我拉下来的东西"的数据。
     */
    private static ClipboardRecordDto copyForUpdate(ClipboardRecordDto source) {
        ClipboardRecordDto dto = new ClipboardRecordDto();
        dto.setSyncId(source.getSyncId());
        dto.setContentType(source.getContentType());
        dto.setContent(source.getContent());
        dto.setContentHash(source.getContentHash());
        dto.setHtmlContent(source.getHtmlContent());
        dto.setPreview(source.getPreview());
        dto.setSourceApp(source.getSourceApp());
        dto.setIsPinned(source.getIsPinned());
        dto.setPinnedOrder(source.getPinnedOrder());
        dto.setTags(source.getTags());
        dto.setNote(source.getNote());
        dto.setUseCount(source.getUseCount());
        dto.setCreatedAt(source.getCreatedAt());
        dto.setLastUsedAt(source.getLastUsedAt());
        dto.setSortAt(source.getSortAt());
        dto.setDeletedAt(source.getDeletedAt());
        // rev 必须带上原值：服务端据此判断"我看到的是不是最新版本"，
        // 不带或写错会变成无条件覆盖。
        dto.setRev(source.getRev());
        return dto;
    }

    private boolean pushUpdate(String userId, ClipboardRecordDto dto, String action) {
        SyncPushRequest request = new SyncPushRequest();
        request.setRecords(List.of(dto));
        request.setSettings(List.of());
        request.setBlobs(List.of());
        try {
            SyncPushResponse response = clipboardSyncService.push(userId, request);
            boolean ok = !response.getAccepted().isEmpty();
            if (!ok && !response.getConflicts().isEmpty()) {
                log.info("[{}] {} 遇到版本冲突：syncId={}", userId, action, dto.getSyncId());
            }
            return ok;
        } catch (Exception e) {
            log.error("[{}] {} 失败：{}", userId, action, e.getMessage(), e);
            return false;
        }
    }

    /** 插入幂等标记；已存在返回 false。 */
    private boolean markSeen(String userId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return true;
        }
        try {
            int rows = jdbcTemplate.update(
                    "INSERT IGNORE INTO t_wechat_msg_seen (client_id, user_id, seen_at) VALUES (?, ?, ?)",
                    clientId, userId, System.currentTimeMillis());
            return rows > 0;
        } catch (Exception e) {
            log.warn("幂等标记写入失败，按未处理继续：{}", e.getMessage());
            return true;
        }
    }

    private void unmarkSeen(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM t_wechat_msg_seen WHERE client_id = ?", clientId);
        } catch (Exception e) {
            log.warn("回滚幂等标记失败：{}", e.getMessage());
        }
    }

    static String syncIdOf(String clientId) {
        // 用 UUID v5 风格的稳定映射：同一 clientId 永远得到同一个 36 位 ID。
        return UUID.nameUUIDFromBytes(("wechat:" + clientId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static Long hashOf(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xff);
            }
            return h;
        } catch (Exception e) {
            return (long) text.hashCode();
        }
    }

    /** 摘要在列表里显示；过长内容截断，避免把一条长文本原样塞进列表。 */
    static String preview(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }
}
