package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.UserPreference;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.FileMapper;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.UserPreferenceMapper;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.RedisLock;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 回收站服务 — 软删除条目查看 / 恢复 / 永久删除 / 定时清理
 * 仅内容级（收藏/便签/云盘文件），目录保持硬删保护
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleService {

    private final BookmarkService bookmarkService;
    private final NoteService noteService;
    private final CloudDiskService cloudDiskService;
    private final VaultService vaultService;

    private final BookmarkMapper bookmarkMapper;
    private final NoteMapper noteMapper;
    private final FileMapper fileMapper;
    private final com.jnclub.bookmark.mapper.VaultMapper vaultMapper;
    private final UserPreferenceMapper userPreferenceMapper;

    private final RedisLock redisLock;

    private static final Duration SCHEDULED_LOCK_TTL = Duration.ofMinutes(10);

    /** 回收站保留天数，默认 30 天（可经 t_user_preference 系统键覆盖，见 getEffectiveKeepDays） */
    @Value("${jnclub.recycle.keep-days:30}")
    private int keepDays;

    /** 系统级偏好保留用户（全局配置，非真实 SSO 用户） */
    private static final String SYSTEM_USER = "__system__";
    private static final String PREF_KEY_KEEP_DAYS = "recycle.keepDays";
    private static final int KEEP_DAYS_MIN = 7;
    private static final int KEEP_DAYS_MAX = 180;

    private static final List<String> VALID_TYPES = List.of("bookmark", "note", "file", "vault");

    // ============================================================
    // 配置（保留天数：t_user_preference 系统键覆盖，yml 默认兜底）
    // ============================================================

    /** 读取生效的保留天数（系统偏好覆盖 > yml 默认） */
    public int getEffectiveKeepDays() {
        UserPreference p = userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, SYSTEM_USER)
                .eq(UserPreference::getPrefKey, PREF_KEY_KEEP_DAYS));
        if (p != null && p.getPrefValue() != null) {
            try {
                int v = Integer.parseInt(p.getPrefValue().trim());
                if (v >= KEEP_DAYS_MIN && v <= KEEP_DAYS_MAX) return v;
            } catch (NumberFormatException ignored) {
                // 脏数据回退默认
            }
        }
        return keepDays;
    }

    /** 更新保留天数（校验 7~180） */
    @Transactional
    public void updateKeepDays(int days) {
        if (days < KEEP_DAYS_MIN || days > KEEP_DAYS_MAX) {
            throw new BizException("保留天数需在 " + KEEP_DAYS_MIN + "~" + KEEP_DAYS_MAX + " 天之间");
        }
        UserPreference p = userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, SYSTEM_USER)
                .eq(UserPreference::getPrefKey, PREF_KEY_KEEP_DAYS));
        if (p == null) {
            p = new UserPreference();
            p.setUserId(SYSTEM_USER);
            p.setPrefKey(PREF_KEY_KEEP_DAYS);
            p.setPrefValue(String.valueOf(days));
            userPreferenceMapper.insert(p);
        } else {
            p.setPrefValue(String.valueOf(days));
            userPreferenceMapper.updateById(p);
        }
        log.info("回收站保留天数更新为 {} 天", days);
    }

    /** 手动立即清理（加锁防与定时任务并发），返回各类型清理计数 */
    public Map<String, Integer> cleanNow() {
        String lockKey = CacheKey.lock("scheduled", "recycle-clean");
        String token = redisLock.tryLock(lockKey, SCHEDULED_LOCK_TTL);
        if (token == null) {
            throw new BizException("清理任务正在进行中，请稍后再试");
        }
        try {
            return cleanExpired();
        } catch (Exception e) {
            log.error("手动清理回收站失败", e);
            throw new BizException("清理失败：" + e.getMessage());
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 列出某类型的回收站条目（倒序）。type: bookmark|note|file|vault
     */
    public List<?> list(String type) {
        String userId = StpUtil.getLoginIdAsString();
        validateType(type);
        return switch (type) {
            case "bookmark" -> bookmarkService.list(new LambdaQueryWrapper<Bookmark>()
                    .eq(Bookmark::getUserId, userId)
                    .eq(Bookmark::getDeleted, 1)
                    .orderByDesc(Bookmark::getCreateTime));
            case "note" -> noteService.list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 1)
                    .orderByDesc(Note::getCreateTime));
            case "vault" -> vaultService.listRecycle(userId);
            default -> cloudDiskService.listRecycle(userId);
        };
    }

    // ============================================================
    // 恢复 / 永久删除
    // ============================================================

    /** 恢复：body = {type, id} */
    public void restore(String type, Long id) {
        String userId = StpUtil.getLoginIdAsString();
        validateType(type);
        switch (type) {
            case "bookmark" -> bookmarkService.restoreBookmark(id, userId);
            case "note" -> noteService.restoreNote(id, userId);
            case "file" -> cloudDiskService.restoreFile(id);
            case "vault" -> vaultService.restore(id, userId);
            default -> throw new BizException("类型非法");
        }
    }

    /** 永久删除：body = {type, id} */
    public void purge(String type, Long id) {
        validateType(type);
        switch (type) {
            case "bookmark" -> bookmarkService.permanentlyDeleteBookmark(id);
            case "note" -> noteService.permanentlyDeleteNote(id);
            case "file" -> cloudDiskService.permanentlyDeleteFile(id);
            case "vault" -> vaultService.permanentlyDelete(id);
            default -> throw new BizException("类型非法");
        }
    }

    /** 清空某类型回收站 */
    public int clear(String type) {
        String userId = StpUtil.getLoginIdAsString();
        validateType(type);
        List<?> items = list(type);
        int count = items.size();
        for (Object item : items) {
            Long id = switch (item) {
                case Bookmark b -> b.getId();
                case Note n -> n.getId();
                case FileRecord f -> f.getId();
                case com.jnclub.bookmark.entity.Vault v -> v.getId();
                default -> throw new IllegalStateException("未知类型");
            };
            purge(type, id);
        }
        log.info("清空回收站: type={} count={} user={}", type, count, userId);
        return count;
    }

    // ============================================================
    // 定时清理（每天 3:40，系统级无登录态）
    // ============================================================

    @Scheduled(cron = "0 40 3 * * ?")
    public void scheduledClean() {
        String lockKey = CacheKey.lock("scheduled", "recycle-clean");
        String token = redisLock.tryLock(lockKey, SCHEDULED_LOCK_TTL);
        if (token == null) {
            log.info("回收站定时清理已被其他实例执行，跳过");
            return;
        }
        try {
            cleanExpired();
        } catch (Exception e) {
            log.error("定时清理回收站失败", e);
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    /**
     * 清理超过保留天数的回收站条目（全用户，无登录态；启动与定时任务调用）
     */
    public Map<String, Integer> cleanExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(getEffectiveKeepDays());
        int bookmark = 0, note = 0, file = 0, vault = 0;

        List<Bookmark> bookmarks = bookmarkMapper.selectList(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getDeleted, 1)
                .lt(Bookmark::getCreateTime, cutoff));
        for (Bookmark b : bookmarks) {
            bookmarkService.purgeByIdNoAuth(b.getId());
            bookmark++;
        }

        List<Note> notes = noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getDeleted, 1)
                .lt(Note::getCreateTime, cutoff));
        for (Note n : notes) {
            noteService.purgeByIdNoAuth(n.getId());
            note++;
        }

        List<FileRecord> files = fileMapper.selectList(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getDeleted, 1)
                .lt(FileRecord::getCreateTime, cutoff));
        for (FileRecord f : files) {
            cloudDiskService.purgeByIdNoAuth(f.getId());
            file++;
        }

        List<com.jnclub.bookmark.entity.Vault> vaults = vaultMapper.selectList(
                new LambdaQueryWrapper<com.jnclub.bookmark.entity.Vault>()
                        .eq(com.jnclub.bookmark.entity.Vault::getDeleted, 1)
                        .lt(com.jnclub.bookmark.entity.Vault::getCreateTime, cutoff));
        for (com.jnclub.bookmark.entity.Vault v : vaults) {
            vaultService.purgeByIdNoAuth(v.getId());
            vault++;
        }

        if (bookmark + note + file + vault > 0) {
            log.info("回收站到期清理: bookmark={} note={} file={} vault={}", bookmark, note, file, vault);
        }
        return Map.of("bookmark", bookmark, "note", note, "file", file, "vault", vault);
    }

    // ============================================================
    // 工具
    // ============================================================

    private void validateType(String type) {
        if (!VALID_TYPES.contains(type)) {
            throw new BizException("类型非法，仅支持 bookmark/note/file");
        }
    }
}
