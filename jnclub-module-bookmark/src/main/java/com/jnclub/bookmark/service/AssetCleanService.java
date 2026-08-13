package com.jnclub.bookmark.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.NoteAsset;
import com.jnclub.bookmark.mapper.NoteAssetMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片资产清理服务
 * — 保存时认领图片（noteId 绑定）
 * — 定时扫描未被引用的孤儿资产
 * — 删除 dufs 文件 + 数据库记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCleanService {

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;

    @Value("${jnclub.dufs.username:}")
    private String dufsUser;

    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    private final NoteMapper noteMapper;
    private final NoteAssetMapper noteAssetMapper;

    private final RedisLock redisLock;

    private static final Pattern MD_IMG_PATTERN =
            Pattern.compile("!\\[[^]]*]\\(/api/files/([^)\\s]+)\\)");

    private static final Duration SCHEDULED_LOCK_TTL = Duration.ofMinutes(10);

    // ========== 定时任务 ==========

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledClean() {
        String lockKey = CacheKey.lock("scheduled", "asset-clean");
        String token = redisLock.tryLock(lockKey, SCHEDULED_LOCK_TTL);
        if (token == null) {
            log.info("孤儿图片定时清理已被其他实例执行，跳过");
            return;
        }
        try {
            log.info("=== 定时清理孤儿图片开始 ===");
            Map<String, Object> result = cleanOrphans(7);
            log.info("=== 定时清理完成: {} ===", result);
        } catch (Exception e) {
            log.error("定时清理孤儿图片失败", e);
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    // ========== 图片认领 ==========

    /**
     * 扫描便签内容中的图片引用，认领匹配的未绑定资产。
     * 在 createNote / updateNote 后调用。
     */
    public void claimAssets(Long noteId, String content) {
        if (content == null || content.isEmpty()) return;

        List<NoteAsset> unclaimed = noteAssetMapper.selectList(
                new LambdaQueryWrapper<NoteAsset>()
                        .isNull(NoteAsset::getNoteId)
                        .eq(NoteAsset::getUserId, getCurrentUserId())
        );

        Set<String> keys = extractKeys(content);
        int claimed = 0;
        for (NoteAsset asset : unclaimed) {
            if (keys.contains(asset.getStoredKey())
                    || (asset.getUrl() != null && keys.contains(stripApiPrefix(asset.getUrl())))) {
                asset.setNoteId(noteId);
                noteAssetMapper.updateById(asset);
                claimed++;
            }
        }
        if (claimed > 0) {
            log.info("认领图片: noteId={} claimed={}", noteId, claimed);
        }
    }

    /**
     * 解绑便签的所有图片资产，在 deleteNote 后调用。
     * 注意：不删除文件，等定时清理来处理。
     */
    public void unclaimAssets(Long noteId) {
        List<NoteAsset> claimed = noteAssetMapper.selectList(
                new LambdaQueryWrapper<NoteAsset>().eq(NoteAsset::getNoteId, noteId)
        );
        for (NoteAsset asset : claimed) {
            asset.setNoteId(null);
            noteAssetMapper.updateById(asset);
        }
        log.info("解绑图片: noteId={} count={}", noteId, claimed.size());
    }

    // ========== 清理方法 ==========

    /**
     * 清理孤儿图片
     * @param minAgeDays 最小存在天数（定时任务默认 7，手动可传 0）
     * @return 清理统计
     */
    public Map<String, Object> cleanOrphans(int minAgeDays) {
        Set<String> referencedKeys = collectAllReferencedKeys();

        LocalDateTime cutoff = LocalDateTime.now().minusDays(minAgeDays);
        // 未认领的图片需要额外保护期：至少存在 1 小时以上才清理
        LocalDateTime unclaimedCutoff = LocalDateTime.now().minusHours(1);
        // 旧数据可能没有 created_at，视为远古资产（可清理）
        LocalDateTime epoch = LocalDateTime.of(2000, 1, 1, 0, 0);

        List<NoteAsset> allAssets = noteAssetMapper.selectList(null);

        int deletedFiles = 0;
        int deletedRecords = 0;
        long freedBytes = 0;

        for (NoteAsset asset : allAssets) {
            if (isReferenced(asset, referencedKeys)) continue;

            boolean shouldDelete = false;
            if (asset.getNoteId() != null) {
                // 已认领：检查对应便签是否还存在且引用该图片
                Note note = noteMapper.selectById(asset.getNoteId());
                if (note == null || note.getContent() == null
                        || !extractKeys(note.getContent()).contains(asset.getStoredKey())) {
                    // 便签已删除或内容已不包含此图片
                    LocalDateTime ca = asset.getCreatedAt() != null ? asset.getCreatedAt() : LocalDateTime.of(2000, 1, 1, 0, 0);
                    if (minAgeDays == 0 || ca.isBefore(cutoff)) {
                        shouldDelete = true;
                    }
                }
            } else {
                // 未认领：只清理超过 1 小时的（保护正在编辑中的上传），且满足 minAgeDays
                // createdAt 可能为 null（历史数据），视为远古资产可清理
                LocalDateTime ca = asset.getCreatedAt() != null ? asset.getCreatedAt() : LocalDateTime.of(2000, 1, 1, 0, 0);
                if (ca.isBefore(unclaimedCutoff)
                        && (minAgeDays == 0 || ca.isBefore(cutoff))) {
                    shouldDelete = true;
                }
            }

            if (shouldDelete) {
                boolean fileDeleted = deleteDufsFile(asset.getStoredKey());
                if (fileDeleted) deletedFiles++;
                noteAssetMapper.deleteById(asset.getId());
                deletedRecords++;
                if (asset.getSize() != null) freedBytes += asset.getSize();
            }
        }

        log.info("清理完成 — 文件:{} 记录:{} 释放:{}B 总扫描:{}",
                deletedFiles, deletedRecords, freedBytes, allAssets.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedFiles", deletedFiles);
        result.put("deletedRecords", deletedRecords);
        result.put("freedBytes", freedBytes);
        result.put("freedMB", Math.round(freedBytes / 10485.76) / 100.0);
        result.put("totalScanned", allAssets.size());
        return result;
    }

    public Map<String, Object> countOrphans(int minAgeDays) {
        Set<String> referencedKeys = collectAllReferencedKeys();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(minAgeDays);
        LocalDateTime unclaimedCutoff = LocalDateTime.now().minusHours(1);

        List<NoteAsset> allAssets = noteAssetMapper.selectList(null);
        long orphanCount = 0;
        long orphanBytes = 0;

        for (NoteAsset asset : allAssets) {
            if (isReferenced(asset, referencedKeys)) continue;

            boolean isOrphan = false;
            if (asset.getNoteId() != null) {
                Note note = noteMapper.selectById(asset.getNoteId());
                if (note == null || note.getContent() == null
                        || !extractKeys(note.getContent()).contains(asset.getStoredKey())) {
                    LocalDateTime ca2 = asset.getCreatedAt() != null ? asset.getCreatedAt() : LocalDateTime.of(2000, 1, 1, 0, 0);
                    if (minAgeDays == 0 || ca2.isBefore(cutoff)) isOrphan = true;
                }
            } else {
                if (asset.getCreatedAt() != null
                        && asset.getCreatedAt().isBefore(unclaimedCutoff)
                        && (minAgeDays == 0 || asset.getCreatedAt().isBefore(cutoff))) {
                    isOrphan = true;
                }
            }

            if (isOrphan) {
                orphanCount++;
                if (asset.getSize() != null) orphanBytes += asset.getSize();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orphanCount", orphanCount);
        result.put("orphanBytes", orphanBytes);
        result.put("orphanMB", Math.round(orphanBytes / 10485.76) / 100.0);
        result.put("totalAssets", allAssets.size());
        return result;
    }

    // ========== 内部方法 ==========

    /**
     * 从 Markdown 内容中提取所有图片 storedKey
     */
    static Set<String> extractKeys(String content) {
        Set<String> keys = new HashSet<>();
        if (content == null || content.isEmpty()) return keys;
        Matcher m = MD_IMG_PATTERN.matcher(content);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    /**
     * 全局扫描：所有便签中引用的图片 key 集合
     */
    private Set<String> collectAllReferencedKeys() {
        Set<String> keys = new HashSet<>();
        List<Note> allNotes = noteMapper.selectList(null);
        for (Note note : allNotes) {
            keys.addAll(extractKeys(note.getContent()));
        }
        return keys;
    }

    private boolean isReferenced(NoteAsset asset, Set<String> referencedKeys) {
        if (referencedKeys.contains(asset.getStoredKey())) return true;
        if (asset.getUrl() != null && referencedKeys.contains(stripApiPrefix(asset.getUrl()))) return true;
        return false;
    }

    private static String stripApiPrefix(String url) {
        return url.startsWith("/api/files/") ? url.substring("/api/files/".length()) : url;
    }

    private boolean deleteDufsFile(String storedKey) {
        try {
            String deleteUrl = dufsBaseUrl + "/" + storedKey;
            var req = HttpRequest.delete(deleteUrl);
            if (dufsUser != null && !dufsUser.isBlank()) {
                String auth = dufsUser + ":" + dufsPass;
                req.header("Authorization", "Basic " +
                        Base64.encode(auth.getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse response = req.execute();
            int status = response.getStatus();
            boolean ok = status == 200 || status == 204 || status == 404;
            if (!ok) {
                log.warn("dufs 删除异常: status={} url={}", status, deleteUrl);
            }
            return ok;
        } catch (Exception e) {
            log.error("dufs 删除失败: key={}", storedKey, e);
            return false;
        }
    }

    /**
     * 获取当前登录用户 ID（供认领/解绑使用）
     */
    private static String getCurrentUserId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return "";
        }
    }
}
