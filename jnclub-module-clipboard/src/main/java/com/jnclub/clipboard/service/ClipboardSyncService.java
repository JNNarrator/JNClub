package com.jnclub.clipboard.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jnclub.clipboard.dto.BlobRefDto;
import com.jnclub.clipboard.dto.ClipboardRecordDto;
import com.jnclub.clipboard.dto.ClipboardSettingDto;
import com.jnclub.clipboard.dto.ConflictDto;
import com.jnclub.clipboard.dto.SyncPullResponse;
import com.jnclub.clipboard.dto.SyncPushRequest;
import com.jnclub.clipboard.dto.SyncPushResponse;
import com.jnclub.clipboard.entity.ClipboardBlob;
import com.jnclub.clipboard.entity.ClipboardRecord;
import com.jnclub.clipboard.entity.ClipboardSetting;
import com.jnclub.clipboard.mapper.ClipboardBlobMapper;
import com.jnclub.clipboard.mapper.ClipboardRecordMapper;
import com.jnclub.clipboard.mapper.ClipboardSettingMapper;
import com.jnclub.clipboard.mapper.ClipboardUserSeqMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 剪贴板跨端同步服务。
 *
 * <h2>协议</h2>
 * <ul>
 *   <li>{@code pull(since, limit)} — 返回 {@code seq > since} 的条目与设置，按 seq 升序。
 *       条目与设置共用一个 seq 空间，所以一次调用就能拿到合并后的增量，
 *       客户端不需要维护两个游标。</li>
 *   <li>{@code push(records, settings)} — 逐条按 {@code rev} 做乐观锁：
 *       rev 更大则覆盖，相等且内容相同视为幂等重放，相等但内容不同或更小判冲突。</li>
 * </ul>
 *
 * <h2>为什么 seq 由服务端分配</h2>
 * 客户端时钟不可信：设备间可能差几分钟甚至几小时。若用客户端时间戳做增量游标，
 * 慢时钟设备写入的数据会被快时钟设备「已经拉过」的游标永久跳过。
 * 服务端分配单调递增 seq 就没有这个问题，顺带给出跨设备的稳定全序。
 *
 * <h2>删除语义</h2>
 * 删除写成墓碑（{@code deleted_at} 非空）而不是物理删除：物理删除后，
 * 已经拉过这条记录的设备永远不知道它被删了，会一直显示幽灵条目。
 * 墓碑同样占用 seq，所以对端能通过 pull 拿到「这条没了」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipboardSyncService {

    /** 单次 pull 的最大条数，防止首次全量同步撑爆响应体 */
    public static final int MAX_PULL_LIMIT = 500;

    public static final int DEFAULT_PULL_LIMIT = 200;

    /**
     * 设备私有键的前缀：这类键描述「这台机器怎么工作」，同步过去只会互相覆盖。
     * 与桌面端 Rust 的 {@code is_setting_sync_eligible} 保持同一套语义。
     */
    private static final String[] DEVICE_PRIVATE_PREFIXES = {"cloud_sync_", "clipboard_"};

    /**
     * 设备私有的具体键。窗口尺寸、匿名 ID、快捷键绑定、本机路径都属于这一类。
     */
    private static final Set<String> DEVICE_PRIVATE_KEYS = Set.of(
            "app.anon_id",
            "app.last_ping_date",
            "app.ping_id",
            "app.window_width",
            "app.window_height",
            "app.tag_manager_size",
            "app.last_edge_dock",
            "app.silent_start",
            "app.silent_start_mac_reset",
            // 已删除功能遗留的键：老库里还有行，但不该因为没人读就开始跨端旅行
            "mqtt_client_id",
            "mqtt_last_retained_sha256",
            "file_server_enabled",
            "file_server_port",
            "file_transfer_path"
    );

    /** 敏感键：密码类一律不上行（客户端本地加密存储即可） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "mqtt_password",
            "mqtt_username",
            "e2e_key",
            "cloud_sync_api_key",
            "cloud_sync_webdav_password"
    );

    private final ClipboardRecordMapper recordMapper;

    private final ClipboardSettingMapper settingMapper;

    private final ClipboardBlobMapper blobMapper;

    private final ClipboardUserSeqMapper seqMapper;

    // ------------------------------------------------------------------ pull

    /**
     * 拉取 {@code since} 之后的变更。
     *
     * @param since 客户端上次拿到的最大 seq，首次同步传 0
     * @param limit 本次最多返回多少条（条目 + 设置合计）
     */
    public SyncPullResponse pull(String userId, long since, int limit) {
        int capped = Math.max(1, Math.min(limit <= 0 ? DEFAULT_PULL_LIMIT : limit, MAX_PULL_LIMIT));
        // 多取一条用来判断 hasMore，比额外发一条 COUNT 便宜
        int probe = capped + 1;

        List<ClipboardRecord> records = recordMapper.selectList(new QueryWrapper<ClipboardRecord>()
                .eq("user_id", userId)
                .gt("seq", since)
                .orderByAsc("seq")
                .last("LIMIT " + probe));

        List<ClipboardSetting> settings = settingMapper.selectList(new QueryWrapper<ClipboardSetting>()
                .eq("user_id", userId)
                .gt("seq", since)
                .orderByAsc("seq")
                .last("LIMIT " + probe));

        // 两个查询各自有序，按 seq 归并后截断，保证客户端看到的是全局有序的增量流
        List<SeqItem> merged = new ArrayList<>(records.size() + settings.size());
        for (ClipboardRecord r : records) {
            merged.add(new SeqItem(r.getSeq(), r, null));
        }
        for (ClipboardSetting s : settings) {
            merged.add(new SeqItem(s.getSeq(), null, s));
        }
        merged.sort(Comparator.comparingLong(SeqItem::seq));

        boolean hasMore = merged.size() > capped;
        List<SeqItem> page = hasMore ? merged.subList(0, capped) : merged;

        List<ClipboardRecordDto> recordDtos = new ArrayList<>(page.size());
        List<ClipboardSettingDto> settingDtos = new ArrayList<>(page.size());
        long nextSeq = since;
        for (SeqItem item : page) {
            nextSeq = item.seq();
            if (item.record() != null) {
                recordDtos.add(toDto(item.record()));
            } else {
                settingDtos.add(toDto(item.setting()));
            }
        }

        SyncPullResponse response = new SyncPullResponse();
        response.setRecords(recordDtos);
        response.setSettings(settingDtos);
        response.setNextSeq(nextSeq);
        response.setHasMore(hasMore);
        return response;
    }

    // ------------------------------------------------------------------ push

    /**
     * 提交本地变更。整体在一个事务里：seq 的分配与写入必须同进同退，
     * 否则回滚后游标已经前进，对端会永远等不到那段区间。
     */
    @Transactional(rollbackFor = Exception.class)
    public SyncPushResponse push(String userId, SyncPushRequest request) {
        List<ClipboardRecordDto> recordDtos = request.getRecords() == null ? List.of() : request.getRecords();
        List<ClipboardSettingDto> settingDtos = request.getSettings() == null ? List.of() : request.getSettings();

        List<String> accepted = new ArrayList<>();
        List<ConflictDto> conflicts = new ArrayList<>();
        List<String> rejectedSettings = new ArrayList<>();

        SeqAllocator allocator = new SeqAllocator(userId, recordDtos.size() + settingDtos.size());

        for (ClipboardRecordDto dto : recordDtos) {
            if (dto == null || dto.getSyncId() == null || dto.getSyncId().isBlank()) {
                continue;
            }
            applyRecord(userId, dto, allocator, accepted, conflicts);
        }

        for (ClipboardSettingDto dto : settingDtos) {
            if (dto == null || dto.getKey() == null || dto.getKey().isBlank()) {
                continue;
            }
            if (!isSettingSyncable(dto.getKey())) {
                rejectedSettings.add(dto.getKey());
                continue;
            }
            applySetting(userId, dto, allocator, accepted);
        }

        applyBlobs(userId, request.getBlobs());

        SyncPushResponse response = new SyncPushResponse();
        response.setAccepted(accepted);
        response.setConflicts(conflicts);
        response.setRejectedSettings(rejectedSettings);
        response.setNextSeq(currentSeq(userId));
        return response;
    }

    /**
     * 写入一条记录。
     *
     * <p>乐观锁判定：
     * <ul>
     *   <li>服务端没有 → 插入</li>
     *   <li>rev 更大 → 覆盖</li>
     *   <li>rev 相等且内容一致 → 幂等重放，接受但不消耗 seq</li>
     *   <li>否则 → 冲突，回传服务端版本</li>
     * </ul>
     */
    private void applyRecord(String userId, ClipboardRecordDto dto, SeqAllocator allocator,
                             List<String> accepted, List<ConflictDto> conflicts) {
        ClipboardRecord existing = recordMapper.selectOne(new QueryWrapper<ClipboardRecord>()
                .eq("user_id", userId)
                .eq("sync_id", dto.getSyncId())
                .last("LIMIT 1"));

        long incomingRev = dto.getRev() == null ? 1L : dto.getRev();

        if (existing == null) {
            ClipboardRecord entity = toEntity(userId, dto);
            entity.setSeq(allocator.next());
            recordMapper.insert(entity);
            accepted.add(dto.getSyncId());
            return;
        }

        long serverRev = existing.getRev() == null ? 1L : existing.getRev();
        if (incomingRev > serverRev) {
            ClipboardRecord entity = toEntity(userId, dto);
            entity.setId(existing.getId());
            entity.setSeq(allocator.next());
            recordMapper.updateById(entity);
            accepted.add(dto.getSyncId());
            return;
        }

        if (incomingRev == serverRev && samePayload(existing, dto)) {
            // 同一版本、同一内容：客户端重试或两台设备恰好算出同样结果，直接接受
            accepted.add(dto.getSyncId());
            return;
        }

        ConflictDto conflict = new ConflictDto();
        conflict.setSyncId(dto.getSyncId());
        conflict.setServerRev(serverRev);
        conflict.setServerRecord(toDto(existing));
        conflicts.add(conflict);
    }

    private void applySetting(String userId, ClipboardSettingDto dto, SeqAllocator allocator,
                              List<String> accepted) {
        ClipboardSetting existing = settingMapper.selectOne(new QueryWrapper<ClipboardSetting>()
                .eq("user_id", userId)
                .eq("setting_key", dto.getKey())
                .last("LIMIT 1"));

        long incomingRev = dto.getRev() == null ? 1L : dto.getRev();
        if (existing != null) {
            long serverRev = existing.getRev() == null ? 1L : existing.getRev();
            if (incomingRev < serverRev) {
                // 设置不做冲突回传：旧版本直接丢弃，客户端下次 pull 会拿到服务端版本
                log.debug("设置 {} 的 rev {} 小于服务端 {}，忽略", dto.getKey(), incomingRev, serverRev);
                return;
            }
            if (incomingRev == serverRev && Objects.equals(existing.getSettingValue(), dto.getValue())
                    && Objects.equals(existing.getDeletedAt(), dto.getDeletedAt())) {
                // 幂等重放，不消耗 seq
                accepted.add(dto.getKey());
                return;
            }
        }

        ClipboardSetting entity = new ClipboardSetting();
        entity.setUserId(userId);
        entity.setSettingKey(dto.getKey());
        entity.setSettingValue(dto.getValue());
        entity.setDeletedAt(dto.getDeletedAt());
        entity.setRev(incomingRev);
        entity.setSeq(allocator.next());

        if (existing == null) {
            settingMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            settingMapper.updateById(entity);
        }
        accepted.add(dto.getKey());
    }

    /** 附件只做「确保元数据存在」，引用计数的增减由记录写入/删除时维护 */
    private void applyBlobs(String userId, List<BlobRefDto> blobs) {
        if (blobs == null || blobs.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (BlobRefDto ref : blobs) {
            if (ref == null || ref.getSha256() == null || !seen.add(ref.getSha256())) {
                continue;
            }
            ClipboardBlob existing = blobMapper.selectOne(new QueryWrapper<ClipboardBlob>()
                    .eq("user_id", userId)
                    .eq("sha256", ref.getSha256())
                    .last("LIMIT 1"));
            if (existing == null) {
                ClipboardBlob entity = new ClipboardBlob();
                entity.setUserId(userId);
                entity.setSha256(ref.getSha256());
                entity.setByteSize(ref.getByteSize() == null ? 0L : ref.getByteSize());
                entity.setMime(ref.getMime());
                entity.setRefCount(1);
                blobMapper.insert(entity);
            } else if (existing.getRefCount() == null || existing.getRefCount() < 1) {
                // 曾被 GC 降到 0 的 blob，对端重新引用时复活
                existing.setRefCount(1);
                blobMapper.updateById(existing);
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 当前用户可见的最大 seq（没有数据时为 0）。
     *
     * <p>用「已落库数据的最大 seq」而不是「已分配游标的上界」：批量分配会预留整段区间，
     * 冲突、幂等重放、被白名单拒掉的设置都会在区间里留下空洞。
     * 拿上界做判断，客户端会永远觉得自己落后，于是反复 pull 空页。
     */
    public long currentSeq(String userId) {
        Long visible = seqMapper.maxVisibleSeq(userId);
        return visible == null ? 0L : visible;
    }

    /** 设置键是否允许跨端同步 */
    public static boolean isSettingSyncable(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (SENSITIVE_KEYS.contains(key)) {
            return false;
        }
        if (DEVICE_PRIVATE_KEYS.contains(key)) {
            return false;
        }
        for (String prefix : DEVICE_PRIVATE_PREFIXES) {
            if (key.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 同版本下判断内容是否一致（用于区分幂等重放与真冲突）。
     *
     * <p>比较前先把入参按 {@link #toEntity} 的同一套默认值归一：
     * 客户端省略 {@code isPinned} 意为「没置顶」，服务端存的是 {@code false}，
     * 两者是同一个意思。若直接比较原始字段，一次正常重试会被判成冲突，
     * 客户端拿到「服务端版本」后又覆盖不掉，于是每轮同步都在同一批条目上报冲突。
     */
    private boolean samePayload(ClipboardRecord entity, ClipboardRecordDto dto) {
        String incomingContentType = dto.getContentType() == null ? "text" : dto.getContentType();
        Boolean incomingPinned = dto.getIsPinned() == null ? Boolean.FALSE : dto.getIsPinned();
        return Objects.equals(entity.getContentType(), incomingContentType)
                && Objects.equals(entity.getContent(), dto.getContent())
                && Objects.equals(entity.getHtmlContent(), dto.getHtmlContent())
                && Objects.equals(entity.getDeletedAt(), dto.getDeletedAt())
                && Objects.equals(entity.getIsPinned(), incomingPinned)
                && Objects.equals(entity.getPinnedOrder(), dto.getPinnedOrder())
                && Objects.equals(entity.getNote(), dto.getNote())
                && Objects.equals(entity.getTags(), tagsToJson(dto.getTags()));
    }

    private ClipboardRecord toEntity(String userId, ClipboardRecordDto dto) {
        ClipboardRecord entity = new ClipboardRecord();
        entity.setUserId(userId);
        entity.setSyncId(dto.getSyncId());
        entity.setContentType(dto.getContentType() == null ? "text" : dto.getContentType());
        entity.setContent(dto.getContent());
        entity.setContentHash(dto.getContentHash() == null ? 0L : dto.getContentHash());
        entity.setHtmlContent(dto.getHtmlContent());
        entity.setPreview(dto.getPreview());
        entity.setSourceApp(dto.getSourceApp());
        entity.setIsPinned(dto.getIsPinned() == null ? Boolean.FALSE : dto.getIsPinned());
        entity.setPinnedOrder(dto.getPinnedOrder());
        entity.setTags(tagsToJson(dto.getTags()));
        entity.setNote(dto.getNote());
        entity.setUseCount(dto.getUseCount() == null ? 0 : dto.getUseCount());
        entity.setCreatedAt(dto.getCreatedAt() == null ? 0L : dto.getCreatedAt());
        entity.setLastUsedAt(dto.getLastUsedAt() == null ? 0L : dto.getLastUsedAt());
        entity.setSortAt(dto.getSortAt() == null ? 0L : dto.getSortAt());
        entity.setDeletedAt(dto.getDeletedAt());
        entity.setRev(dto.getRev() == null ? 1L : dto.getRev());
        return entity;
    }

    private ClipboardRecordDto toDto(ClipboardRecord entity) {
        ClipboardRecordDto dto = new ClipboardRecordDto();
        dto.setSyncId(entity.getSyncId());
        dto.setContentType(entity.getContentType());
        dto.setContent(entity.getContent());
        dto.setContentHash(entity.getContentHash());
        dto.setHtmlContent(entity.getHtmlContent());
        dto.setPreview(entity.getPreview());
        dto.setSourceApp(entity.getSourceApp());
        dto.setIsPinned(entity.getIsPinned());
        dto.setPinnedOrder(entity.getPinnedOrder());
        dto.setTags(tagsFromJson(entity.getTags()));
        dto.setNote(entity.getNote());
        dto.setUseCount(entity.getUseCount());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setLastUsedAt(entity.getLastUsedAt());
        dto.setSortAt(entity.getSortAt());
        dto.setDeletedAt(entity.getDeletedAt());
        dto.setRev(entity.getRev());
        dto.setSeq(entity.getSeq());
        return dto;
    }

    private ClipboardSettingDto toDto(ClipboardSetting entity) {
        ClipboardSettingDto dto = new ClipboardSettingDto();
        dto.setKey(entity.getSettingKey());
        dto.setValue(entity.getSettingValue());
        dto.setDeletedAt(entity.getDeletedAt());
        dto.setRev(entity.getRev());
        dto.setSeq(entity.getSeq());
        return dto;
    }

    private String tagsToJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        return JSONUtil.toJsonStr(tags);
    }

    private List<String> tagsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            log.warn("标签 JSON 解析失败，按空处理: {}", json);
            return List.of();
        }
    }

    /** pull 归并用的中间结构：同一个 seq 空间里要么是条目，要么是设置 */
    private record SeqItem(long seq, ClipboardRecord record, ClipboardSetting setting) {
    }

    /**
     * seq 批量分配器。
     *
     * <p>一次 push 先把整个区间预留下来（{@code INSERT IGNORE} 建行 →
     * {@code SELECT ... FOR UPDATE} 锁行 → 一次写回上界），
     * 然后在内存里逐个发号。相比「每条记录都锁一次、写一次」，
     * 200 条的 push 从 400 次往返降到 3 次。
     *
     * <p>冲突和幂等重放不消耗号码，所以区间里会留空洞——这不影响正确性：
     * 客户端只关心 {@code seq > since}，空洞不会被读到。
     */
    private final class SeqAllocator {

        private final String userId;

        private long next;

        private long upper;

        private SeqAllocator(String userId, int reserve) {
            this.userId = userId;
            seqMapper.insertSeqIfAbsent(userId);
            Long locked = seqMapper.lockSeq(userId);
            long base = locked == null ? 0L : locked;
            this.next = base;
            this.upper = base + Math.max(0, reserve);
            if (reserve > 0) {
                seqMapper.updateSeq(userId, this.upper);
            }
        }

        private long next() {
            return ++next;
        }
    }
}
