package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URL;
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
        return list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getDirectoryId, directoryId)
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0)
                .orderByAsc(Bookmark::getSortOrder));
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

        save(bookmark);
        return bookmark;
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
    }

    /** 无鉴权永久删除（回收站定时清理用，跳过登录态校验） */
    public void purgeByIdNoAuth(Long id) {
        Bookmark bookmark = getById(id);
        if (bookmark == null) return;
        removeById(id);
        tagService.deleteRelationsByRef("bookmark", id, bookmark.getUserId());
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
}
