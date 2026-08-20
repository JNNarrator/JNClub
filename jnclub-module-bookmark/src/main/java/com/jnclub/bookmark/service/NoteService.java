package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.NoteVersion;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.mapper.NoteMapper;
import com.jnclub.bookmark.mapper.NoteVersionMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.CacheService;
import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 便签服务 — 标题自动派生 + 图片认领 + 批量排序 + 置顶/归档/历史版本
 */
@Slf4j
@Service
public class NoteService extends ServiceImpl<NoteMapper, Note> {

    @Autowired
    private AssetCleanService assetCleanService;

    @Autowired
    private TagService tagService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private DirectoryMapper directoryMapper;

    @Autowired
    private NoteVersionMapper noteVersionMapper;

    public List<Note> getNotes(Long directoryId, Long tagId) {
        return getNotes(directoryId, tagId, false);
    }

    /**
     * 获取便签列表
     *
     * @param directoryId 目录ID
     * @param tagId       标签ID（可选）
     * @param archived    归档视图：true 时返回该用户全部已归档便签（跨目录），false 只返回正常便签
     */
    public List<Note> getNotes(Long directoryId, Long tagId, boolean archived) {
        String userId = StpUtil.getLoginIdAsString();
        if (archived) {
            // 归档视图：全部已归档便签，置顶优先、时间倒序
            List<Note> notes = list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .eq(Note::getArchived, 1)
                    .orderByDesc(Note::getPinned)
                    .orderByDesc(Note::getUpdateTime));
            notes.forEach(n -> n.setTitle(deriveDisplayTitle(n.getTitle(), n.getContent())));
            return notes;
        }

        // 正常视图：置顶优先，其余按 sort_order
        List<Note> notes;
        if (tagId != null) {
            List<Long> refIds = tagService.listRefIdsByTag("note", tagId, userId);
            if (refIds.isEmpty()) return List.of();
            notes = list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getDirectoryId, directoryId)
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .eq(Note::getArchived, 0)
                    .in(Note::getId, refIds)
                    .orderByDesc(Note::getPinned)
                    .orderByAsc(Note::getSortOrder));
        } else {
            // 无标签过滤：走 Redis 旁路缓存（列表含派生标题，读开销大）
            String cacheKey = CacheKey.note(userId, directoryId);
            List<Note> cached = cacheService.getList(cacheKey, Note.class);
            if (cached != null) return cached;
            notes = list(new LambdaQueryWrapper<Note>()
                    .eq(Note::getDirectoryId, directoryId)
                    .eq(Note::getUserId, userId)
                    .eq(Note::getDeleted, 0)
                    .eq(Note::getArchived, 0)
                    .orderByDesc(Note::getPinned)
                    .orderByAsc(Note::getSortOrder));
            for (Note note : notes) {
                note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
            }
            cacheService.setList(cacheKey, notes, CacheService.DEFAULT_TTL);
            return notes;
        }

        for (Note note : notes) {
            note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        }
        return notes;
    }

    public Note getNoteDetail(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        return note;
    }

    @Transactional
    public Note createNote(Note note) {
        String userId = StpUtil.getLoginIdAsString();
        note.setUserId(userId);
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        save(note);

        assetCleanService.claimAssets(note.getId(), note.getContent());
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
        return note;
    }

    @Transactional
    public void updateNote(Long id, Note note) {
        String userId = StpUtil.getLoginIdAsString();
        Note existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }

        // 内容/标题变化时保存历史版本快照（内容不同才存）
        String oldTitle = existing.getTitle();
        String oldContent = existing.getContent();
        String newTitle = note.getTitle();
        String newContent = note.getContent();
        boolean contentChanged = !equalsNullable(oldContent, newContent) || !equalsNullable(oldTitle, newTitle);
        if (contentChanged) {
            saveVersionSnapshot(existing, userId);
        }

        existing.setTitle(newTitle);
        existing.setContent(newContent);
        existing.setTitle(deriveDisplayTitle(existing.getTitle(), existing.getContent()));

        updateById(existing);

        assetCleanService.claimAssets(existing.getId(), existing.getContent());
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    private boolean equalsNullable(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /** 保存便签当前状态为历史版本快照（版本号自增） */
    private void saveVersionSnapshot(Note note, String userId) {
        try {
            NoteVersion v = new NoteVersion();
            v.setNoteId(note.getId());
            v.setUserId(userId);
            v.setTitle(note.getTitle());
            v.setContent(note.getContent());
            Integer maxVersion = noteVersionMapper.selectCount(new LambdaQueryWrapper<NoteVersion>()
                    .eq(NoteVersion::getNoteId, note.getId())).intValue();
            v.setVersionNo(maxVersion + 1);
            noteVersionMapper.insert(v);

            // 只保留最近 50 个版本，防止无限膨胀
            List<NoteVersion> all = noteVersionMapper.selectList(new LambdaQueryWrapper<NoteVersion>()
                    .eq(NoteVersion::getNoteId, note.getId())
                    .orderByDesc(NoteVersion::getVersionNo));
            if (all.size() > 50) {
                List<Long> toDelete = new ArrayList<>();
                for (int i = 50; i < all.size(); i++) toDelete.add(all.get(i).getId());
                noteVersionMapper.deleteBatchIds(toDelete);
            }
        } catch (Exception e) {
            log.warn("保存便签版本快照失败 noteId={}: {}", note.getId(), e.getMessage());
        }
    }

    @Transactional
    public void deleteNote(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        // 软删除：进入回收站。内容仍引用图片，故不 unclaim（恢复后图片依旧可用）
        note.setDeleted(1);
        updateById(note);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 移动便签到其他目录（type=2 目录校验 + 缓存失效） */
    @Transactional
    public void moveNote(Long id, Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BizException("便签不存在");
        }
        if (directoryId == null) {
            throw new BizException("请选择目标目录");
        }
        checkDirOwnership(directoryId, userId);
        if (note.getDirectoryId() != null && note.getDirectoryId().equals(directoryId)) {
            throw new BizException("已在该目录中");
        }
        note.setDirectoryId(directoryId);
        updateById(note);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 批量移动便签（跳过已在该目录的项；type=2 目录校验） */
    @Transactional
    public void moveNotesBatch(List<Long> ids, Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        if (directoryId == null) throw new BizException("请选择目标目录");
        checkDirOwnership(directoryId, userId);
        for (Long id : ids) {
            Note n = getById(id);
            if (n == null || !n.getUserId().equals(userId)) continue;
            if (n.getDirectoryId() != null && n.getDirectoryId().equals(directoryId)) continue;
            n.setDirectoryId(directoryId);
            updateById(n);
        }
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 批量删除便签（软删除进回收站） */
    @Transactional
    public void deleteNotesBatch(List<Long> ids) {
        String userId = StpUtil.getLoginIdAsString();
        for (Long id : ids) {
            Note n = getById(id);
            if (n == null || !n.getUserId().equals(userId)
                    || (n.getDeleted() != null && n.getDeleted() == 1)) continue;
            n.setDeleted(1);
            updateById(n);
        }
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 目标目录归属 + type 校验（便签目录 type=2，模式同 CloudDiskService.checkDirOwnership） */
    private void checkDirOwnership(Long directoryId, String userId) {
        Directory dir = directoryMapper.selectById(directoryId);
        if (dir == null || !dir.getUserId().equals(userId)) {
            throw new BizException("目录不存在");
        }
        if (dir.getType() == null || dir.getType() != 2) {
            throw new BizException("目标目录不是便签目录");
        }
    }

    /**
     * 永久删除便签（回收站清空/到期清理用）：物理删记录 + 解绑图片 + 级联删标签关联
     */
    @Transactional
    public void permanentlyDeleteNote(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        removeById(id);
        assetCleanService.unclaimAssets(id);
        tagService.deleteRelationsByRef("note", id, userId);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 从回收站恢复便签 */
    public void restoreNote(Long id, String userId) {
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new RuntimeException("便签不存在");
        }
        if (note.getDeleted() == null || note.getDeleted() != 1) {
            throw new RuntimeException("便签不在回收站中");
        }
        note.setDeleted(0);
        updateById(note);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 无鉴权永久删除（回收站定时清理用，跳过登录态校验） */
    @Transactional
    public void purgeByIdNoAuth(Long id) {
        Note note = getById(id);
        if (note == null) return;
        removeById(id);
        assetCleanService.unclaimAssets(id);
        tagService.deleteRelationsByRef("note", id, note.getUserId());
        cacheService.evictByPrefix(CacheKey.notePrefix(note.getUserId()));
    }

    @Transactional
    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = StpUtil.getLoginIdAsString();
        List<Note> toUpdate = new ArrayList<>();
        for (Map<String, Object> item : sortList) {
            Long id = Long.parseLong(item.get("id").toString());
            Integer sortOrder = Integer.parseInt(item.get("sortOrder").toString());

            Note note = getById(id);
            if (note != null && note.getUserId().equals(userId)) {
                note.setSortOrder(sortOrder);
                toUpdate.add(note);
            }
        }
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
        }
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    // ============================================================
    // 置顶 / 归档 / 历史版本
    // ============================================================

    /** 置顶/取消置顶 */
    @Transactional
    public void setPinned(Long id, boolean pinned) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = requireOwnedNote(id, userId);
        note.setPinned(pinned ? 1 : 0);
        updateById(note);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 归档/取消归档 */
    @Transactional
    public void setArchived(Long id, boolean archived) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = requireOwnedNote(id, userId);
        note.setArchived(archived ? 1 : 0);
        // 归档时自动取消置顶
        if (archived) note.setPinned(0);
        updateById(note);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 历史版本列表（版本号倒序，不含完整内容以减负） */
    public List<Map<String, Object>> listVersions(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        requireOwnedNote(id, userId);
        List<NoteVersion> versions = noteVersionMapper.selectList(new LambdaQueryWrapper<NoteVersion>()
                .eq(NoteVersion::getNoteId, id)
                .orderByDesc(NoteVersion::getVersionNo));
        List<Map<String, Object>> result = new ArrayList<>();
        for (NoteVersion v : versions) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", v.getId());
            item.put("versionNo", v.getVersionNo());
            item.put("title", v.getTitle());
            item.put("createTime", v.getCreateTime());
            result.add(item);
        }
        return result;
    }

    /** 历史版本详情（含完整内容） */
    public NoteVersion getVersion(Long noteId, Long versionId) {
        String userId = StpUtil.getLoginIdAsString();
        requireOwnedNote(noteId, userId);
        NoteVersion v = noteVersionMapper.selectById(versionId);
        if (v == null || !v.getNoteId().equals(noteId)) {
            throw new BizException("版本不存在");
        }
        return v;
    }

    /** 回滚到指定版本：当前状态先存快照，再覆盖为指定版本内容 */
    @Transactional
    public void restoreVersion(Long noteId, Long versionId) {
        String userId = StpUtil.getLoginIdAsString();
        Note note = requireOwnedNote(noteId, userId);
        NoteVersion v = noteVersionMapper.selectById(versionId);
        if (v == null || !v.getNoteId().equals(noteId)) {
            throw new BizException("版本不存在");
        }
        // 回滚前保存当前状态快照（防误回滚丢失）
        saveVersionSnapshot(note, userId);
        note.setTitle(v.getTitle());
        note.setContent(v.getContent());
        note.setTitle(deriveDisplayTitle(note.getTitle(), note.getContent()));
        updateById(note);
        cacheService.evictByPrefix(CacheKey.notePrefix(userId));
    }

    /** 校验便签归属当前用户 */
    private Note requireOwnedNote(Long id, String userId) {
        Note note = getById(id);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BizException("便签不存在");
        }
        return note;
    }

    private String deriveDisplayTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (content != null && !content.isBlank()) {
            String[] lines = content.strip().split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
                if (trimmed.startsWith("## ")) {
                    return trimmed.substring(3).trim();
                }
            }
            String plain = content.replaceAll("[#*`\\[\\]()!>_~\\-]", "").trim();
            if (plain.length() > 30) {
                return plain.substring(0, 30) + "…";
            }
            return plain;
        }
        return "无标题";
    }
}
