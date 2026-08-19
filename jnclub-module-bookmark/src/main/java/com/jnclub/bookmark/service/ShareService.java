package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.FileRecord;
import com.jnclub.bookmark.entity.Note;
import com.jnclub.bookmark.entity.Share;
import com.jnclub.bookmark.mapper.ShareMapper;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开只读分享服务 — 便签 / 收藏 / 云盘文件生成公开链接（可选密码 + 有效期）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareMapper shareMapper;
    private final NoteService noteService;
    private final BookmarkService bookmarkService;
    private final CloudDiskService cloudDiskService;

    private static final List<String> VALID_TYPES = List.of("note", "bookmark", "file");

    /** 生成分享：password 为空=免密；expiresInDays 为空=永不过期 */
    public Map<String, Object> create(String refType, Long refId, String password, Integer expiresInDays) {
        if (!VALID_TYPES.contains(refType) || refId == null) {
            throw new BizException("分享类型非法");
        }
        String userId = StpUtil.getLoginIdAsString();
        // 归属校验
        switch (refType) {
            case "note" -> ensureNoteOwned(refId, userId);
            case "bookmark" -> ensureBookmarkOwned(refId, userId);
            case "file" -> cloudDiskService.getFile(refId); // 内部已校验归属
        }
        // 幂等：同条目已存在分享则复用 token（除非更新了参数）
        Share existing = findForRef(refType, refId);
        Share sh = existing != null ? existing : new Share();
        if (sh.getToken() == null) {
            sh.setToken(IdUtil.simpleUUID());
        }
        sh.setRefType(refType);
        sh.setRefId(refId);
        sh.setUserId(userId);
        sh.setPasswordHash(password == null || password.isBlank() ? null : BCrypt.hashpw(password));
        sh.setExpiresAt(expiresInDays == null || expiresInDays <= 0 ? null : LocalDateTime.now().plusDays(expiresInDays));
        if (existing != null) {
            shareMapper.updateById(sh);
        } else {
            shareMapper.insert(sh);
        }
        return Map.of("token", sh.getToken(), "url", "/jnclub/share/" + sh.getToken());
    }

    /** 查询某条目的分享（我的） */
    public List<Share> listByRef(String refType, Long refId) {
        String userId = StpUtil.getLoginIdAsString();
        return shareMapper.selectList(new LambdaQueryWrapper<Share>()
                .eq(Share::getRefType, refType)
                .eq(Share::getRefId, refId)
                .eq(Share::getUserId, userId));
    }

    /** 撤销分享（仅归属用户） */
    public void revoke(String token) {
        String userId = StpUtil.getLoginIdAsString();
        Share sh = shareMapper.selectById(token);
        if (sh == null || !sh.getUserId().equals(userId)) {
            throw new BizException("分享不存在");
        }
        shareMapper.deleteById(token);
    }

    /** 公开解析分享内容：返回 { type, ...payload } 或 { locked:true, hasPassword:true } */
    public Map<String, Object> resolve(String token, String password) {
        Share sh = shareMapper.selectById(token);
        if (sh == null) {
            throw new BizException(404, "分享不存在或已失效");
        }
        if (sh.getExpiresAt() != null && sh.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(410, "分享已过期");
        }
        if (sh.getPasswordHash() != null) {
            if (password == null || !BCrypt.checkpw(password, sh.getPasswordHash())) {
                Map<String, Object> locked = new LinkedHashMap<>();
                locked.put("locked", true);
                locked.put("hasPassword", true);
                return locked;
            }
        }
        return payload(sh);
    }

    /** 校验 token 可用于访问文件流（密码/过期检查），返回记录 */
    public FileRecord resolveFile(String token, String password) {
        Map<String, Object> r = resolve(token, password);
        if (Boolean.TRUE.equals(r.get("locked"))) {
            throw new BizException(403, "需要访问密码");
        }
        Share sh = shareMapper.selectById(token);
        if (!"file".equals(sh.getRefType())) {
            throw new BizException("该分享不是文件");
        }
        return cloudDiskService.getFileById(sh.getRefId());
    }

    private Map<String, Object> payload(Share sh) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", sh.getRefType());
        switch (sh.getRefType()) {
            case "note" -> {
                Note n = noteService.getById(sh.getRefId());
                if (n == null) throw new BizException(404, "内容不存在");
                m.put("title", n.getTitle());
                m.put("content", n.getContent());
                m.put("createTime", n.getCreateTime());
            }
            case "bookmark" -> {
                Bookmark b = bookmarkService.getById(sh.getRefId());
                if (b == null) throw new BizException(404, "内容不存在");
                m.put("title", b.getTitle());
                m.put("url", b.getUrl());
                m.put("icon", b.getIcon());
            }
            case "file" -> {
                FileRecord f = cloudDiskService.getFileById(sh.getRefId());
                if (f == null) throw new BizException(404, "内容不存在");
                m.put("name", f.getOriginalName());
                m.put("mime", f.getMime());
                m.put("size", f.getSize());
            }
            default -> throw new BizException("类型非法");
        }
        return m;
    }

    private Share findForRef(String refType, Long refId) {
        String userId = StpUtil.getLoginIdAsString();
        return shareMapper.selectOne(new LambdaQueryWrapper<Share>()
                .eq(Share::getRefType, refType)
                .eq(Share::getRefId, refId)
                .eq(Share::getUserId, userId));
    }

    private void ensureNoteOwned(Long id, String userId) {
        Note n = noteService.getById(id);
        if (n == null || !n.getUserId().equals(userId)) throw new BizException("便签不存在");
    }
    private void ensureBookmarkOwned(Long id, String userId) {
        Bookmark b = bookmarkService.getById(id);
        if (b == null || !b.getUserId().equals(userId)) throw new BizException("收藏不存在");
    }
}
