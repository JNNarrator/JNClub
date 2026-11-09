package com.jnclub.clipboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jnclub.clipboard.entity.ClipboardBlob;
import com.jnclub.clipboard.mapper.ClipboardBlobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 剪贴板附件的延期 GC。
 *
 * <h2>为什么必须先有引用计数才能有 GC</h2>
 * 图片的字节存 dufs、元数据存 {@code t_clipboard_blob}。引用计数由
 * {@link ClipboardSyncService} 在记录写入/删除时维护：归零时记
 * {@code zeroedAt}，本任务只清理「归零超过 {@value #RETENTION_DAYS} 天」的附件。
 * 没有 GC 时附件无限堆积；先上 GC 后修计数则更糟——所有历史图片会被
 * 一夜清光，客户端的 {@code <img>} 会全部变成 404。
 *
 * <h2>为什么保留期是 7 天</h2>
 * 计数归零意味着「已知的记录都不再引用它」。但设备可能离线好几天：
 * 一台周三合上盖子的笔记本，周一打开后 pull 到的墓碑只是把它本地的
 * 引用放掉，而不是把服务器上早已清理的字节要回来。保留期给离线设备
 * 留出「先拉墓碑、后清字节」的窗口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipboardBlobGcService {

    /** 计数归零后的保留天数，离线设备的时间窗口。 */
    private static final int RETENTION_DAYS = 7;

    /** 单轮最多处理多少行，避免一次 GC 把大附件库锁太久。 */
    private static final int BATCH = 500;

    private final ClipboardBlobMapper blobMapper;
    private final ClipboardBlobService blobService;

    /**
     * 每天凌晨 3:55（避开书签/音乐模块已占用的 3 点档）清理过期附件。
     */
    @Scheduled(cron = "0 55 3 * * ?")
    public void sweep() {
        int removed = sweepOnce(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (removed > 0) {
            log.info("剪贴板附件 GC 完成: 清理 {} 个", removed);
        }
    }

    /**
     * 单轮清理：先删 dufs 实体，成功后才删元数据行——顺序反过来会在
     * dufs 失败时留下「有行无文件」的假引用，对端拉取永远 404。
     *
     * @return 实际清理的行数
     */
    public int sweepOnce(LocalDateTime cutoff) {
        List<ClipboardBlob> stale = blobMapper.selectList(new QueryWrapper<ClipboardBlob>()
                .le("ref_count", 0)
                .isNotNull("zeroed_at")
                .lt("zeroed_at", cutoff)
                .last("LIMIT " + BATCH));
        int removed = 0;
        for (ClipboardBlob blob : stale) {
            try {
                if (!blobService.deleteFromDufs(blob.getUserId(), blob.getSha256())) {
                    continue;
                }
                blobMapper.deleteById(blob.getId());
                removed += 1;
            } catch (Exception e) {
                log.warn("附件 GC 单行失败（下轮重试）: sha={} err={}", blob.getSha256(), e.getMessage());
            }
        }
        return removed;
    }
}
