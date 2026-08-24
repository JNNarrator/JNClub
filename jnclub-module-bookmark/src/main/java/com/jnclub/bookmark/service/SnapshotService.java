package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.BookmarkSnapshot;
import com.jnclub.bookmark.mapper.BookmarkSnapshotMapper;
import com.jnclub.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏网页快照服务 — 抓取页面 HTML 存 dufs + 元数据入库 + 快照读取/删除
 * 与失效检测形成闭环：收藏 → 归档快照 → 失效后仍可查看快照兜底阅读
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final BookmarkSnapshotMapper snapshotMapper;
    private final BookmarkService bookmarkService;

    @Value("${jnclub.dufs.base-url}")
    private String dufsBaseUrl;
    @Value("${jnclub.dufs.username:}")
    private String dufsUser;
    @Value("${jnclub.dufs.password:}")
    private String dufsPass;

    private static final String SNAPSHOT_PATH = "/jnclub/snapshots/";
    /** 抓取上限：2MB（超出截断，防止拖垮后端） */
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** UA 同失效检测（避免被目标站识别为爬虫） */
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** 抓取并归档快照（幂等：重复抓取覆盖旧快照） */
    public Map<String, Object> capture(Long bookmarkId) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark b = bookmarkService.getById(bookmarkId);
        if (b == null || !b.getUserId().equals(userId)) {
            throw new BizException("收藏不存在");
        }

        // 1. 抓取页面（UA 同失效检测；失败抛业务异常）
        String html = fetchHtml(b.getUrl());
        if (html == null || html.isBlank()) {
            throw new BizException("页面抓取失败（可能无法访问或非 HTML 内容）");
        }
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        // 2. 计算快照 key：固定路径，重复抓取覆盖
        String dufsKey = SNAPSHOT_PATH + userId + "/" + bookmarkId + ".html";

        // 3. 推 dufs
        boolean pushed = pushToDufs(dufsKey, bytes);
        if (!pushed) {
            throw new BizException("快照保存失败，请稍后重试");
        }

        // 4. 入库（存在则更新，否则插入）
        BookmarkSnapshot snap = find(bookmarkId, userId);
        if (snap == null) {
            snap = new BookmarkSnapshot();
            snap.setBookmarkId(bookmarkId);
            snap.setUserId(userId);
            snap.setCapturedAt(LocalDateTime.now());
        }
        snap.setDufsKey(dufsKey);
        snap.setTitle(b.getTitle());
        snap.setUrl(b.getUrl());
        snap.setSize(bytes.length);
        snap.setDeleted(0);
        if (snap.getId() == null) {
            snapshotMapper.insert(snap);
        } else {
            snapshotMapper.updateById(snap);
        }

        log.info("收藏快照归档完成: bookmark={} size={}B", bookmarkId, bytes.length);
        return meta(snap);
    }

    /** 快照元信息（登录态） */
    public Map<String, Object> get(Long bookmarkId) {
        String userId = StpUtil.getLoginIdAsString();
        BookmarkSnapshot snap = find(bookmarkId, userId);
        if (snap == null) {
            throw new BizException(404, "暂无快照");
        }
        return meta(snap);
    }

    /** 是否存在快照（供失效检测列表附带） */
    public boolean has(Long bookmarkId, String userId) {
        BookmarkSnapshot snap = find(bookmarkId, userId);
        return snap != null;
    }

    /** 删除快照：dufs 对象 + 记录（幂等：无快照也返回成功） */
    public void delete(Long bookmarkId) {
        String userId = StpUtil.getLoginIdAsString();
        BookmarkSnapshot snap = find(bookmarkId, userId);
        if (snap == null) return;
        deleteDufsFile(snap.getDufsKey());
        snapshotMapper.deleteById(snap.getId());
    }

    /** 快照内容字节（登录态，从 dufs 拉取） */
    public byte[] content(Long bookmarkId) {
        String userId = StpUtil.getLoginIdAsString();
        BookmarkSnapshot snap = find(bookmarkId, userId);
        if (snap == null) {
            throw new BizException(404, "暂无快照");
        }
        try {
            var req = HttpRequest.get(dufsBaseUrl + snap.getDufsKey()).timeout(15000);
            if (dufsUser != null && !dufsUser.isBlank()) {
                req.header("Authorization", "Basic " + Base64.encode(
                        (dufsUser + ":" + dufsPass).getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse resp = req.execute();
            if (resp.getStatus() != 200) {
                log.warn("快照 dufs 读取失败: status={} key={}", resp.getStatus(), snap.getDufsKey());
                throw new BizException(404, "快照内容不可用");
            }
            return resp.bodyBytes();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("快照 dufs 读取异常: key={}", snap.getDufsKey(), e);
            throw new BizException(500, "快照读取失败");
        }
    }

    // ======================== 内部方法 ========================

    private BookmarkSnapshot find(Long bookmarkId, String userId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<BookmarkSnapshot>()
                .eq(BookmarkSnapshot::getBookmarkId, bookmarkId)
                .eq(BookmarkSnapshot::getUserId, userId)
                .eq(BookmarkSnapshot::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private Map<String, Object> meta(BookmarkSnapshot snap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", snap.getId());
        m.put("bookmarkId", snap.getBookmarkId());
        m.put("title", snap.getTitle());
        m.put("url", snap.getUrl());
        m.put("size", snap.getSize());
        m.put("capturedAt", snap.getCapturedAt());
        return m;
    }

    /** 抓取页面 HTML（带 UA/超时/大小上限），失败返回 null */
    private String fetchHtml(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            HttpResponse resp = HttpRequest.get(url)
                    .setFollowRedirects(true)
                    .timeout(15000)
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .execute();
            if (resp.getStatus() != 200) {
                log.debug("快照抓取 HTTP {} for {}", resp.getStatus(), url);
                return null;
            }
            byte[] body = resp.bodyBytes();
            if (body.length > MAX_BYTES) {
                body = java.util.Arrays.copyOf(body, MAX_BYTES);
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("快照抓取失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private boolean pushToDufs(String dufsKey, byte[] bytes) {
        try {
            var req = HttpRequest.put(dufsBaseUrl + dufsKey)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body(bytes);
            if (dufsUser != null && !dufsUser.isBlank()) {
                req.header("Authorization", "Basic " + Base64.encode(
                        (dufsUser + ":" + dufsPass).getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse resp = req.execute();
            int status = resp.getStatus();
            if (status != 200 && status != 201) {
                log.error("快照 dufs 上传失败: status={}", status);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("快照 dufs 上传异常", e);
            return false;
        }
    }

    private void deleteDufsFile(String dufsKey) {
        try {
            var req = HttpRequest.delete(dufsBaseUrl + dufsKey);
            if (dufsUser != null && !dufsUser.isBlank()) {
                req.header("Authorization", "Basic " + Base64.encode(
                        (dufsUser + ":" + dufsPass).getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse resp = req.execute();
            int status = resp.getStatus();
            if (status != 200 && status != 204 && status != 404) {
                log.warn("快照 dufs 删除异常: status={} key={}", status, dufsKey);
            }
        } catch (Exception e) {
            log.warn("快照 dufs 删除失败: key={}", dufsKey, e);
        }
    }

    /** 无鉴权删除（回收站清理等场景用）；失败静默 */
    public void deleteNoAuth(Long bookmarkId, String userId) {
        BookmarkSnapshot snap = find(bookmarkId, userId);
        if (snap == null) return;
        deleteDufsFile(snap.getDufsKey());
        snapshotMapper.deleteById(snap.getId());
    }
}
