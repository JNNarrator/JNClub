package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.bookmark.entity.Feed;
import com.jnclub.bookmark.entity.FeedItem;
import com.jnclub.bookmark.mapper.FeedItemMapper;
import com.jnclub.bookmark.mapper.FeedMapper;
import com.jnclub.common.exception.BizException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RSS 阅读器服务 — 订阅源管理 / 抓取解析（Rome）/ 已读星标 / 一键收藏打通
 * 定时任务每 30 分钟按间隔抓取全部订阅源（RedisLock 防重）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedMapper feedMapper;
    private final FeedItemMapper feedItemMapper;
    private final BookmarkService bookmarkService;
    private final com.jnclub.common.cache.RedisLock redisLock;

    /** 初始拉取条目上限 */
    private static final int INITIAL_LIMIT = 50;
    /** 单次抓取最大条目数 */
    private static final int FETCH_LIMIT = 50;
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // ======================== 订阅源管理 ========================

    /** 添加订阅源：抓取验证 + 解析标题/图标 + 初始拉取 */
    @Transactional
    public Map<String, Object> addFeed(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("订阅地址不能为空");
        }
        String userId = StpUtil.getLoginIdAsString();
        String trimmed = url.trim();

        // 已订阅则直接返回（幂等）
        Feed exist = feedMapper.selectOne(new LambdaQueryWrapper<Feed>()
                .eq(Feed::getUserId, userId).eq(Feed::getDeleted, 0).eq(Feed::getUrl, trimmed));
        if (exist != null) {
            throw new BizException("该源已订阅");
        }

        // 抓取 + 解析验证
        SyndFeed synd = fetchSyndFeed(trimmed);
        if (synd == null) {
            throw new BizException("无法解析该地址（不是有效的 RSS/Atom 源）");
        }

        Feed feed = new Feed();
        feed.setUserId(userId);
        feed.setUrl(trimmed);
        feed.setTitle(cleanText(synd.getTitle(), 300, trimmed));
        if (synd.getLink() != null) feed.setSiteUrl(cleanText(synd.getLink(), 1024, ""));
        feed.setIcon("");
        feed.setFetchIntervalMin(30);
        feed.setDeleted(0);
        feedMapper.insert(feed);

        // 初始拉取
        int inserted = ingestItems(feed, synd, INITIAL_LIMIT);
        feed.setLastFetchedAt(LocalDateTime.now());
        feedMapper.updateById(feed);

        log.info("订阅成功: feed={} title={} items={}", feed.getId(), feed.getTitle(), inserted);
        return Map.of("id", feed.getId(), "title", feed.getTitle(), "items", inserted);
    }

    /** 我的订阅源列表（附带未读数） */
    public List<Map<String, Object>> listFeeds() {
        String userId = StpUtil.getLoginIdAsString();
        List<Feed> feeds = feedMapper.selectList(new LambdaQueryWrapper<Feed>()
                .eq(Feed::getUserId, userId).eq(Feed::getDeleted, 0)
                .orderByDesc(Feed::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Feed f : feeds) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("url", f.getUrl());
            m.put("title", f.getTitle());
            m.put("siteUrl", f.getSiteUrl());
            m.put("icon", f.getIcon());
            m.put("lastFetchedAt", f.getLastFetchedAt());
            m.put("fetchIntervalMin", f.getFetchIntervalMin());
            m.put("unread", unreadCount(f.getId(), userId));
            result.add(m);
        }
        return result;
    }

    /** 删除订阅源（级联删条目） */
    @Transactional
    public void deleteFeed(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Feed feed = requireOwned(id, userId);
        feed.setDeleted(1);
        feedMapper.updateById(feed);
        // 软删除条目
        List<FeedItem> items = feedItemMapper.selectList(new LambdaQueryWrapper<FeedItem>()
                .eq(FeedItem::getFeedId, id).eq(FeedItem::getUserId, userId).eq(FeedItem::getDeleted, 0));
        for (FeedItem it : items) {
            it.setDeleted(1);
            feedItemMapper.updateById(it);
        }
    }

    /** 手动刷新单个源 */
    public Map<String, Object> fetchFeed(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Feed feed = requireOwned(id, userId);
        SyndFeed synd = fetchSyndFeed(feed.getUrl());
        if (synd == null) {
            throw new BizException("抓取失败（源不可达或解析失败）");
        }
        int inserted = ingestItems(feed, synd, FETCH_LIMIT);
        feed.setLastFetchedAt(LocalDateTime.now());
        if (synd.getTitle() != null && !synd.getTitle().isBlank()) {
            feed.setTitle(cleanText(synd.getTitle(), 300, feed.getTitle()));
        }
        feedMapper.updateById(feed);
        return Map.of("feedId", id, "newItems", inserted);
    }

    // ======================== 条目操作 ========================

    /** 条目列表：filter = all|unread|starred；分页 */
    public Map<String, Object> listItems(Long feedId, String filter, int page, int size) {
        String userId = StpUtil.getLoginIdAsString();
        LambdaQueryWrapper<FeedItem> qw = new LambdaQueryWrapper<FeedItem>()
                .eq(FeedItem::getUserId, userId)
                .eq(FeedItem::getDeleted, 0);
        if (feedId != null && feedId > 0) qw.eq(FeedItem::getFeedId, feedId);
        String f = filter == null ? "all" : filter;
        if ("unread".equals(f)) qw.eq(FeedItem::getReadFlag, 0);
        if ("starred".equals(f)) qw.eq(FeedItem::getStarred, 1);
        qw.orderByDesc(FeedItem::getPublishedAt).orderByDesc(FeedItem::getId);

        long total = feedItemMapper.selectCount(qw);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 50);
        qw.last("LIMIT " + ((p - 1) * s) + ", " + s);

        List<FeedItem> items = feedItemMapper.selectList(qw);
        // 兼容历史脏数据：读取时再清洗一遍，前端 v-html 始终只拿到安全 HTML
        items.forEach(it -> {
            if (it.getContent() != null) it.setContent(sanitizeHtml(it.getContent()));
            if (it.getSummary() != null) it.setSummary(sanitizeHtml(it.getSummary()));
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", p);
        result.put("size", s);
        result.put("items", items);
        return result;
    }

    /** 标记已读 */
    public void markRead(Long itemId) {
        String userId = StpUtil.getLoginIdAsString();
        FeedItem it = requireItemOwned(itemId, userId);
        it.setReadFlag(1);
        feedItemMapper.updateById(it);
    }

    /** 全部已读（可限定源） */
    public int markAllRead(Long feedId) {
        String userId = StpUtil.getLoginIdAsString();
        List<FeedItem> items = feedItemMapper.selectList(new LambdaQueryWrapper<FeedItem>()
                .eq(FeedItem::getUserId, userId).eq(FeedItem::getDeleted, 0)
                .eq(FeedItem::getReadFlag, 0)
                .eq(feedId != null && feedId > 0, FeedItem::getFeedId, feedId));
        for (FeedItem it : items) {
            it.setReadFlag(1);
            feedItemMapper.updateById(it);
        }
        return items.size();
    }

    /** 切换星标 */
    public void toggleStar(Long itemId) {
        String userId = StpUtil.getLoginIdAsString();
        FeedItem it = requireItemOwned(itemId, userId);
        it.setStarred(it.getStarred() != null && it.getStarred() == 1 ? 0 : 1);
        feedItemMapper.updateById(it);
    }

    /** 一键收藏到收藏夹（打通 bookmark） */
    public Map<String, Object> itemToBookmark(Long itemId, Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        FeedItem it = requireItemOwned(itemId, userId);
        Bookmark b = new Bookmark();
        b.setTitle(it.getTitle() == null || it.getTitle().isBlank() ? "RSS 条目" : it.getTitle());
        b.setUrl(it.getLink() == null || it.getLink().isBlank() ? "" : it.getLink());
        b.setDirectoryId(directoryId);
        b.setSortOrder(0);
        if (it.getTitle() != null && !it.getTitle().isBlank()) b.setTitle(it.getTitle());
        Bookmark saved = bookmarkService.addBookmark(b);
        // 顺手标已读
        it.setReadFlag(1);
        feedItemMapper.updateById(it);
        return Map.of("bookmarkId", saved.getId());
    }

    /** 全部未读数（导航角标） */
    public long unreadTotal() {
        String userId = StpUtil.getLoginIdAsString();
        Long c = feedItemMapper.selectCount(new LambdaQueryWrapper<FeedItem>()
                .eq(FeedItem::getUserId, userId)
                .eq(FeedItem::getDeleted, 0)
                .eq(FeedItem::getReadFlag, 0));
        return c == null ? 0 : c;
    }

    // ======================== 定时任务 ========================

    /** 每 30 分钟尝试抓取全部源（按 last_fetched_at + interval 跳过未到期） */
    @Scheduled(cron = "0 */30 * * * ?")
    public void fetchAllScheduled() {
        String lockKey = com.jnclub.common.cache.CacheKey.lock("scheduled", "rss-fetch");
        String token = redisLock.tryLock(lockKey, java.time.Duration.ofMinutes(25));
        if (token == null) {
            log.info("RSS 定时抓取已被其他实例执行，跳过");
            return;
        }
        try {
            List<Feed> feeds = feedMapper.selectList(new LambdaQueryWrapper<Feed>()
                    .eq(Feed::getDeleted, 0));
            for (Feed feed : feeds) {
                try {
                    if (feed.getLastFetchedAt() != null) {
                        int interval = feed.getFetchIntervalMin() == null ? 30 : feed.getFetchIntervalMin();
                        if (feed.getLastFetchedAt().plusMinutes(interval).isAfter(LocalDateTime.now())) {
                            continue; // 未到间隔
                        }
                    }
                    SyndFeed synd = fetchSyndFeed(feed.getUrl());
                    if (synd == null) {
                        log.warn("定时抓取失败 feed={} url={}", feed.getId(), feed.getUrl());
                        continue;
                    }
                    int inserted = ingestItems(feed, synd, FETCH_LIMIT);
                    feed.setLastFetchedAt(LocalDateTime.now());
                    feedMapper.updateById(feed);
                    if (inserted > 0) {
                        log.info("定时抓取 feed={} 新增 {} 条", feed.getId(), inserted);
                    }
                    Thread.sleep(300); // 串行限速
                } catch (Exception e) {
                    log.warn("定时抓取异常 feed={}: {}", feed.getId(), e.getMessage());
                }
            }
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    // ======================== 内部方法 ========================

    /** 抓取 + Rome 解析（失败返回 null） */
    private SyndFeed fetchSyndFeed(String url) {
        try {
            HttpResponse resp = HttpRequest.get(url)
                    .setFollowRedirects(true)
                    .timeout(15000)
                    .header("User-Agent", UA)
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                    .execute();
            if (resp.getStatus() != 200) {
                log.debug("RSS 抓取 HTTP {} for {}", resp.getStatus(), url);
                return null;
            }
            byte[] body = resp.bodyBytes();
            if (body.length > 5 * 1024 * 1024) {
                body = java.util.Arrays.copyOf(body, 5 * 1024 * 1024);
            }
            SyndFeedInput input = new SyndFeedInput();
            return input.build(new XmlReader(new java.io.ByteArrayInputStream(body), StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            log.debug("RSS 解析失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** 入库条目（guid 去重，存在则跳过） */
    private int ingestItems(Feed feed, SyndFeed synd, int limit) {
        int inserted = 0;
        List<SyndEntry> entries = synd.getEntries();
        if (entries == null) return 0;
        int n = Math.min(entries.size(), limit);
        for (int i = 0; i < n; i++) {
            SyndEntry e = entries.get(i);
            String guid = e.getUri() != null ? e.getUri() : e.getLink();
            if (guid == null || guid.isBlank()) continue;
            if (guid.length() > 500) guid = guid.substring(0, 500);

            // guid 去重
            Long c = feedItemMapper.selectCount(new LambdaQueryWrapper<FeedItem>()
                    .eq(FeedItem::getFeedId, feed.getId())
                    .eq(FeedItem::getGuid, guid));
            if (c != null && c > 0) continue;

            FeedItem item = new FeedItem();
            item.setFeedId(feed.getId());
            item.setUserId(feed.getUserId());
            item.setGuid(guid);
            item.setTitle(cleanText(e.getTitle(), 500, "（无标题）"));
            if (e.getLink() != null) item.setLink(cleanText(e.getLink(), 2048, ""));
            if (e.getAuthor() != null) item.setAuthor(cleanText(e.getAuthor(), 200, ""));
            if (e.getDescription() != null) item.setSummary(sanitizeHtml(cleanText(e.getDescription().getValue(), 4000, "")));
            if (e.getContents() != null && !e.getContents().isEmpty()) {
                item.setContent(sanitizeHtml(e.getContents().get(0).getValue()));
            }
            item.setPublishedAt(e.getPublishedDate() == null ? null
                    : e.getPublishedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            item.setReadFlag(0);
            item.setStarred(0);
            item.setDeleted(0);
            try {
                feedItemMapper.insert(item);
                inserted++;
            } catch (Exception ex) {
                // 唯一索引冲突：并发重复插入，忽略
                log.debug("条目插入跳过（重复）: feed={} guid={}", feed.getId(), guid);
            }
        }
        return inserted;
    }

    private long unreadCount(Long feedId, String userId) {
        Long c = feedItemMapper.selectCount(new LambdaQueryWrapper<FeedItem>()
                .eq(FeedItem::getFeedId, feedId)
                .eq(FeedItem::getUserId, userId)
                .eq(FeedItem::getDeleted, 0)
                .eq(FeedItem::getReadFlag, 0));
        return c == null ? 0 : c;
    }

    private Feed requireOwned(Long id, String userId) {
        Feed feed = feedMapper.selectById(id);
        if (feed == null || !feed.getUserId().equals(userId) || (feed.getDeleted() != null && feed.getDeleted() == 1)) {
            throw new BizException("订阅源不存在");
        }
        return feed;
    }

    private FeedItem requireItemOwned(Long id, String userId) {
        FeedItem it = feedItemMapper.selectById(id);
        if (it == null || !it.getUserId().equals(userId) || (it.getDeleted() != null && it.getDeleted() == 1)) {
            throw new BizException("条目不存在");
        }
        return it;
    }

    /** 文本清理：null 安全 + 截断 + 去空白折叠 */
    private String cleanText(String s, int max, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** HTML 清洗：RSS 正文/摘要来自不可信源，前端会 v-html 渲染，必须去掉脚本/事件属性 */
    private String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.clean(html, Safelist.relaxed()
                .addAttributes("img", "src", "alt", "width", "height")
                .addAttributes("a", "href", "title", "target", "rel")
                .addAttributes("code", "class")
                .addAttributes("pre", "class"));
    }
}
