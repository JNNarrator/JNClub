package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.CacheService;
import com.jnclub.common.exception.BizException;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BookmarkService extends ServiceImpl<BookmarkMapper, Bookmark> {

    @Autowired
    private TagService tagService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private DirectoryMapper directoryMapper;

    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    public List<Bookmark> getBookmarks(Long directoryId, Long tagId) {
        String userId = StpUtil.getLoginIdAsString();
        if (tagId != null) {
            // 按标签筛选：先从关联表取 refId 集合
            List<Long> refIds = tagService.listRefIdsByTag("bookmark", tagId, userId);
            if (refIds.isEmpty()) return List.of();
            return list(new LambdaQueryWrapper<Bookmark>()
                    .eq(Bookmark::getDirectoryId, directoryId)
                    .eq(Bookmark::getUserId, userId)
                    .eq(Bookmark::getDeleted, 0)
                    .in(Bookmark::getId, refIds)
                    .orderByAsc(Bookmark::getSortOrder));
        }
        // 无标签过滤：读多写少，走 Redis 旁路缓存
        String cacheKey = CacheKey.bookmark(userId, directoryId);
        List<Bookmark> cached = cacheService.getList(cacheKey, Bookmark.class);
        if (cached != null) return cached;
        List<Bookmark> result = list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getDirectoryId, directoryId)
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0)
                .orderByAsc(Bookmark::getSortOrder));
        cacheService.setList(cacheKey, result, CacheService.DEFAULT_TTL);
        return result;
    }

    public Bookmark addBookmark(Bookmark bookmark) {
        String userId = StpUtil.getLoginIdAsString();
        bookmark.setUserId(userId);

        boolean needMeta = bookmark.getTitle() == null || bookmark.getTitle().isBlank()
                || bookmark.getIcon() == null || bookmark.getIcon().isBlank();

        if (needMeta) {
            Map<String, String> meta = fetchPageMeta(bookmark.getUrl());
            if (meta != null) {
                if (bookmark.getTitle() == null || bookmark.getTitle().isBlank()) {
                    bookmark.setTitle(meta.get("title"));
                }
                if (bookmark.getIcon() == null || bookmark.getIcon().isBlank()) {
                    bookmark.setIcon(meta.get("icon"));
                }
            }
        }

        // 最终兜底
        if (bookmark.getTitle() == null || bookmark.getTitle().isBlank()) {
            bookmark.setTitle(fallbackTitle(bookmark.getUrl()));
        }
        if (bookmark.getIcon() == null || bookmark.getIcon().isBlank()) {
            bookmark.setIcon(fallbackIcon(bookmark.getUrl()));
        }

        // 字段长度兜底：抓取的 favicon/标题可能超过列长度上限，先归一化再落库
        sanitizeForInsert(bookmark);

        save(bookmark);
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
        return bookmark;
    }

    /**
     * 字段长度兜底：title/icon/url 分别有列长度上限（200/2048/2048），
     * 超长会导致 INSERT 抛 DataIntegrityViolationException，这里统一归一化。
     * 超长 favicon 直接置空（前端有占位兜底），避免截断出无效坏链。
     */
    static void sanitizeForInsert(Bookmark bookmark) {
        if (bookmark.getIcon() != null && bookmark.getIcon().length() > 2048) {
            bookmark.setIcon(null);
        }
        if (bookmark.getTitle() != null && bookmark.getTitle().length() > 200) {
            bookmark.setTitle(bookmark.getTitle().substring(0, 200));
        }
        if (bookmark.getUrl() != null && bookmark.getUrl().length() > 2048) {
            bookmark.setUrl(bookmark.getUrl().substring(0, 2048));
        }
    }

    /**
     * 预览网页元数据：标题 + favicon（失败返回 null，绝不抛异常）
     */
    public Map<String, String> fetchPageMeta(String urlStr) {
        try {
            HttpResponse response = HttpRequest.get(urlStr)
                    .setFollowRedirects(true)
                    .timeout(10000)
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .execute();

            if (response.getStatus() != 200) {
                log.debug("fetchPageMeta HTTP {} for {}", response.getStatus(), urlStr);
                return null;
            }

            String html = response.body();
            if (html == null || html.isBlank()) return null;

            Map<String, String> result = new LinkedHashMap<>();
            result.put("title", extractTitle(html, urlStr));
            result.put("icon", extractFaviconFromHtml(html, urlStr));
            return result;
        } catch (Exception e) {
            log.debug("fetchPageMeta error for {}: {}", urlStr, e.getMessage());
            return null;
        }
    }

    public void updateBookmark(Long id, Bookmark bookmark) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("收藏不存在");
        }
        existing.setTitle(bookmark.getTitle());
        existing.setUrl(bookmark.getUrl());
        if (bookmark.getIcon() != null) existing.setIcon(bookmark.getIcon());
        updateById(existing);
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 移动收藏到其他目录（type=1 目录校验 + 缓存失效） */
    public void moveBookmark(Long id, Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark bookmark = getById(id);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new BizException("收藏不存在");
        }
        if (directoryId == null) {
            throw new BizException("请选择目标目录");
        }
        checkDirOwnership(directoryId, userId);
        if (bookmark.getDirectoryId() != null && bookmark.getDirectoryId().equals(directoryId)) {
            throw new BizException("已在该目录中");
        }
        bookmark.setDirectoryId(directoryId);
        updateById(bookmark);
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 目标目录归属 + type 校验（收藏夹目录 type=1，模式同 CloudDiskService.checkDirOwnership） */
    /** 重复收藏检测：按规范化 URL 分组返回重复组（组内 ≥2 条） */
    public List<Map<String, Object>> listDuplicates() {
        String userId = StpUtil.getLoginIdAsString();
        List<Bookmark> bookmarks = list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0));
        Map<String, List<Bookmark>> byKey = new LinkedHashMap<>();
        for (Bookmark b : bookmarks) {
            String key = normalizeUrl(b.getUrl());
            if (key.isEmpty()) continue;
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        byKey.forEach((key, list) -> {
            if (list.size() < 2) return;
            List<Map<String, Object>> items = new ArrayList<>();
            for (Bookmark b : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", b.getId());
                m.put("title", b.getTitle());
                m.put("url", b.getUrl());
                m.put("icon", b.getIcon());
                m.put("directoryId", b.getDirectoryId());
                items.add(m);
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("url", list.get(0).getUrl());
            group.put("normalized", key);
            group.put("count", list.size());
            group.put("items", items);
            groups.add(group);
        });
        groups.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));
        return groups;
    }

    /** URL 规范化：去协议 → 去 www. → 去尾斜杠 → 去锚点（统一小写） */
    private String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        u = u.replaceFirst("^https?://", "");
        u = u.replaceFirst("^www\\.", "");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private void checkDirOwnership(Long directoryId, String userId) {
        Directory dir = directoryMapper.selectById(directoryId);
        if (dir == null || !dir.getUserId().equals(userId)) {
            throw new BizException("目录不存在");
        }
        if (dir.getType() == null || dir.getType() != 1) {
            throw new BizException("目标目录不是收藏夹目录");
        }
    }

    public void deleteBookmark(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark bookmark = getById(id);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new RuntimeException("收藏不存在");
        }
        // 软删除：进入回收站（保留标签关联，恢复后仍可见）
        bookmark.setDeleted(1);
        updateById(bookmark);
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 批量移动收藏（跳过已在该目录的项；type=1 目录校验） */
    @Transactional
    public void moveBookmarksBatch(List<Long> ids, Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        if (directoryId == null) throw new BizException("请选择目标目录");
        checkDirOwnership(directoryId, userId);
        for (Long id : ids) {
            Bookmark b = getById(id);
            if (b == null || !b.getUserId().equals(userId)) continue;
            if (b.getDirectoryId() != null && b.getDirectoryId().equals(directoryId)) continue;
            b.setDirectoryId(directoryId);
            updateById(b);
        }
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 批量删除收藏（软删除进回收站） */
    @Transactional
    public void deleteBookmarksBatch(List<Long> ids) {
        String userId = StpUtil.getLoginIdAsString();
        for (Long id : ids) {
            Bookmark b = getById(id);
            if (b == null || !b.getUserId().equals(userId)
                    || (b.getDeleted() != null && b.getDeleted() == 1)) continue;
            b.setDeleted(1);
            updateById(b);
        }
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 批量设置标签（全量覆盖式，同 setRelations） */
    @Transactional
    public void setTagsBatch(List<Long> ids, List<String> tagNames) {
        String userId = StpUtil.getLoginIdAsString();
        for (Long id : ids) {
            Bookmark b = getById(id);
            if (b == null || !b.getUserId().equals(userId)) continue;
            tagService.setRelations("bookmark", id, tagNames);
        }
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 永久删除收藏（回收站清空/到期清理用）：物理删记录 + 级联删标签关联 */
    public void permanentlyDeleteBookmark(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark bookmark = getById(id);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new RuntimeException("收藏不存在");
        }
        removeById(id);
        tagService.deleteRelationsByRef("bookmark", id, userId);
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 从回收站恢复收藏 */
    public void restoreBookmark(Long id, String userId) {
        Bookmark bookmark = getById(id);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new RuntimeException("收藏不存在");
        }
        if (bookmark.getDeleted() == null || bookmark.getDeleted() != 1) {
            throw new RuntimeException("收藏不在回收站中");
        }
        bookmark.setDeleted(0);
        updateById(bookmark);
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    /** 无鉴权永久删除（回收站定时清理用，跳过登录态校验） */
    public void purgeByIdNoAuth(Long id) {
        Bookmark bookmark = getById(id);
        if (bookmark == null) return;
        removeById(id);
        tagService.deleteRelationsByRef("bookmark", id, bookmark.getUserId());
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(bookmark.getUserId()));
    }

    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = StpUtil.getLoginIdAsString();
        for (Map<String, Object> item : sortList) {
            Long id = Long.parseLong(item.get("id").toString());
            Integer sortOrder = Integer.parseInt(item.get("sortOrder").toString());
            Bookmark bookmark = getById(id);
            if (bookmark != null && bookmark.getUserId().equals(userId)) {
                bookmark.setSortOrder(sortOrder);
                updateById(bookmark);
            }
        }
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
    }

    // ======================== 提取 ========================

    private String extractTitle(String html, String urlStr) {
        // 优先 og:title（更友好）
        Pattern og = Pattern.compile("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = og.matcher(html);
        if (m.find()) return cleanTitle(m.group(1));

        // 其次 <title>
        Pattern tp = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
        m = tp.matcher(html);
        if (m.find()) return cleanTitle(m.group(1));

        return fallbackTitle(urlStr);
    }

    private String extractFaviconFromHtml(String html, String urlStr) {
        try {
            Pattern pattern = Pattern.compile(
                    "<link[^>]+rel=[\"'](?:shortcut )?icon[\"'][^>]+href=[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String href = matcher.group(1);
                return resolveUrl(href, urlStr);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveUrl(String href, String baseUrl) {
        try {
            if (href.startsWith("//")) return "https:" + href;
            if (href.startsWith("/")) {
                URL u = new URL(baseUrl);
                return u.getProtocol() + "://" + u.getHost() + href;
            }
            if (!href.startsWith("http")) {
                URL u = new URL(baseUrl);
                return u.getProtocol() + "://" + u.getHost() + "/" + href;
            }
            return href;
        } catch (Exception e) { return href; }
    }

    // ======================== 兜底 ========================

    private String fallbackTitle(String urlStr) {
        try {
            URL u = new URL(urlStr);
            String host = u.getHost();
            // bilibili.com → Bilibili
            if (host.startsWith("www.")) host = host.substring(4);
            int dot = host.lastIndexOf('.');
            if (dot > 0) host = host.substring(0, dot);
            return host.substring(0, 1).toUpperCase() + host.substring(1);
        } catch (Exception e) { return urlStr; }
    }

    private String fallbackIcon(String urlStr) {
        try {
            URL u = new URL(urlStr);
            String host = u.getHost();
            if (host.startsWith("www.")) host = host.substring(4);
            return "https://www.google.com/s2/favicons?domain=" + host + "&sz=64";
        } catch (Exception e) { return null; }
    }

    private String cleanTitle(String title) {
        return title == null ? null : title.replaceAll("\\s+", " ").trim();
    }

    // ============================================================
    // 收藏失效检测（死链检测）：HEAD 探测 + 状态落库 + 限速
    // ============================================================

    /**
     * 检测当前用户全部收藏的链接可用性。
     * 串行 + 每次间隔（避免触发目标站反爬/被封），状态写 t_bookmark.check_status。
     * 返回 { total, ok, dead, error, deadList }
     */
    public Map<String, Object> checkDeadLinks() {
        String userId = StpUtil.getLoginIdAsString();
        List<Bookmark> bookmarks = list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0));
        if (bookmarks.isEmpty()) {
            return Map.of("total", 0, "ok", 0, "dead", 0, "error", 0, "deadList", List.of());
        }

        int ok = 0, dead = 0, error = 0;
        List<Map<String, Object>> deadList = new java.util.ArrayList<>();
        for (Bookmark b : bookmarks) {
            int status = probeUrl(b.getUrl());
            if (status == 1) ok++;
            else if (status == 2) {
                dead++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", b.getId());
                item.put("title", b.getTitle());
                item.put("url", b.getUrl());
                deadList.add(item);
            } else {
                error++;
            }
            b.setCheckStatus(status);
            b.setCheckedAt(java.time.LocalDateTime.now());
            updateById(b);
            // 限速：每请求间隔 120ms，平衡速度与被封风险
            try { Thread.sleep(120); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }

        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", bookmarks.size());
        result.put("ok", ok);
        result.put("dead", dead);
        result.put("error", error);
        result.put("deadList", deadList);
        return result;
    }

    /** 失效收藏列表（check_status=2） */
    public List<Map<String, Object>> listDeadLinks() {
        String userId = StpUtil.getLoginIdAsString();
        List<Bookmark> dead = list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0)
                .eq(Bookmark::getCheckStatus, 2)
                .orderByDesc(Bookmark::getCheckedAt));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Bookmark b : dead) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", b.getId());
            item.put("title", b.getTitle());
            item.put("url", b.getUrl());
            item.put("checkedAt", String.valueOf(b.getCheckedAt()));
            result.add(item);
        }
        return result;
    }

    /** 批量删除失效收藏（真正删除；回收站保留） */
    @Transactional
    public int deleteDeadLinks(List<Long> ids) {
        String userId = StpUtil.getLoginIdAsString();
        int count = 0;
        for (Long id : ids) {
            Bookmark b = getById(id);
            if (b != null && b.getUserId().equals(userId) && b.getCheckStatus() != null && b.getCheckStatus() == 2) {
                removeById(id);
                tagService.deleteRelationsByRef("bookmark", id, userId);
                count++;
            }
        }
        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
        return count;
    }

    /**
     * 探测单个 URL：返回 1=正常 2=失效 0=无法判断（网络错误/超时）
     * HEAD 优先，部分站点不支持 HEAD 则回退 GET（不下载 body）
     */
    private int probeUrl(String url) {
        if (url == null || url.isBlank()) return 0;
        try {
            // 1) HEAD
            try (HttpResponse resp = HttpRequest.head(url)
                    .timeout(10_000)
                    .header("User-Agent", UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .execute()) {
                int code = resp.getStatus();
                if (code >= 200 && code < 400) return 1;
                if (code >= 400) return 2;
                return 0;
            } catch (Exception e) {
                // 2) HEAD 失败（部分站点拒绝）→ GET 只读状态
                try (HttpResponse resp = HttpRequest.get(url)
                        .timeout(10_000)
                        .header("User-Agent", UA)
                        .header("Accept", "*/*")
                        .execute()) {
                    int code = resp.getStatus();
                    if (code >= 200 && code < 400) return 1;
                    if (code >= 400) return 2;
                    return 0;
                }
            }
        } catch (Exception e) {
            log.debug("收藏链接探测失败 {}: {}", url, e.getMessage());
            return 2; // 网络不可达视为失效
        }
    }
}
