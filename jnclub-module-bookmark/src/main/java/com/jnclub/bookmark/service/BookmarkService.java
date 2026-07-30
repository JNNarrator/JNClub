package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.mapper.BookmarkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页收藏服务
 */
@Slf4j
@Service
public class BookmarkService extends ServiceImpl<BookmarkMapper, Bookmark> {

    /**
     * 获取目录下的收藏列表
     */
    public List<Bookmark> getBookmarks(Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        return list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getDirectoryId, directoryId)
                .eq(Bookmark::getUserId, userId)
                .orderByAsc(Bookmark::getSortOrder));
    }

    /**
     * 添加收藏
     */
    public Bookmark addBookmark(Bookmark bookmark) {
        String userId = StpUtil.getLoginIdAsString();
        bookmark.setUserId(userId);
        
        // 自动提取 Favicon
        if (bookmark.getIcon() == null || bookmark.getIcon().isBlank()) {
            bookmark.setIcon(extractFavicon(bookmark.getUrl()));
        }
        
        save(bookmark);
        return bookmark;
    }

    /**
     * 编辑收藏
     */
    public void updateBookmark(Long id, Bookmark bookmark) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("收藏不存在");
        }
        
        existing.setTitle(bookmark.getTitle());
        existing.setUrl(bookmark.getUrl());
        if (bookmark.getIcon() != null) {
            existing.setIcon(bookmark.getIcon());
        }
        
        updateById(existing);
    }

    /**
     * 删除收藏
     */
    public void deleteBookmark(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Bookmark bookmark = getById(id);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new RuntimeException("收藏不存在");
        }
        removeById(id);
    }

    /**
     * 批量更新排序
     */
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

    /**
     * 提取 Favicon
     */
    private String extractFavicon(String urlStr) {
        try {
            URL url = new URL(urlStr);
            String domain = url.getHost();
            
            // 策略1：尝试 /favicon.ico
            String faviconUrl = "https://" + domain + "/favicon.ico";
            if (checkUrl(faviconUrl)) {
                return faviconUrl;
            }
            
            // 策略2：解析 HTML 中的 link rel="icon"
            String htmlFavicon = extractFaviconFromHtml(urlStr);
            if (htmlFavicon != null) {
                return htmlFavicon;
            }
            
            // 策略3：使用 Google Favicon 服务
            return "https://www.google.com/s2/favicons?domain=" + domain + "&sz=64";
        } catch (Exception e) {
            log.warn("提取 Favicon 失败: {}", urlStr, e);
            return null;
        }
    }

    /**
     * 检查 URL 是否可访问
     */
    private boolean checkUrl(String url) {
        try {
            HttpResponse response = HttpRequest.head(url)
                    .timeout(3000)
                    .execute();
            return response.getStatus() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 HTML 中提取 Favicon
     */
    private String extractFaviconFromHtml(String urlStr) {
        try {
            HttpResponse response = HttpRequest.get(urlStr)
                    .timeout(5000)
                    .execute();
            
            if (response.getStatus() != 200) {
                return null;
            }
            
            String html = response.body();
            
            // 匹配 <link rel="icon" href="...">
            Pattern pattern = Pattern.compile("<link[^>]*rel=[\"'](?:shortcut )?icon[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            
            if (matcher.find()) {
                String href = matcher.group(1);
                
                // 处理相对路径
                if (href.startsWith("//")) {
                    return "https:" + href;
                } else if (href.startsWith("/")) {
                    URL url = new URL(urlStr);
                    return url.getProtocol() + "://" + url.getHost() + href;
                } else if (!href.startsWith("http")) {
                    URL url = new URL(urlStr);
                    return url.getProtocol() + "://" + url.getHost() + "/" + href;
                }
                
                return href;
            }
            
            return null;
        } catch (Exception e) {
            log.debug("解析 HTML Favicon 失败: {}", urlStr, e);
            return null;
        }
    }
}
