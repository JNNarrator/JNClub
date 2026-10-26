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

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    // 收藏失效检测（死链检测）：并行 HEAD 探测 + 状态批量落库
    // ============================================================

    /** 探测并发度：8 个线程足以在 20~100 条收藏规模下把耗时压到秒级，同时避免触发目标站反爬 */
    private static final int PROBE_THREADS = 8;

    /** 单次探测（HEAD/GET）超时：5s。域名不可达时不再叠加第二次 5s，见 probeUrl 的回退条件 */
    private static final int PROBE_TIMEOUT_MS = 5_000;

    /** 整批探测的总预算（秒）：超时后取消未完成任务并记为「无法判断」，保证接口一定在 60s 内返回 */
    private static final long PROBE_BATCH_TIMEOUT_SECONDS = 40L;

    /**
     * 检测当前用户全部收藏的链接可用性。
     * 并行探测（有界线程池 8 线程）→ 主线程批量写回 t_bookmark.check_status / checked_at。
     * 返回 { total, ok, dead, error, deadList }，deadList 每项 { id, title, url }。
     */
    public Map<String, Object> checkDeadLinks() {
        String userId = StpUtil.getLoginIdAsString();
        List<Bookmark> bookmarks = list(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getDeleted, 0));
        if (bookmarks.isEmpty()) {
            return Map.of("total", 0, "ok", 0, "dead", 0, "error", 0, "deadList", List.of());
        }

        // 1) 并行探测：worker 线程只返回 (id, status)，不碰数据库
        Map<Long, Integer> statusById = probeAll(bookmarks);

        // 2) 主线程聚合结果 + 组装批量更新实体
        int ok = 0, dead = 0, error = 0;
        List<Map<String, Object>> deadList = new ArrayList<>();
        List<Bookmark> patches = new ArrayList<>(bookmarks.size());
        LocalDateTime now = LocalDateTime.now();
        for (Bookmark b : bookmarks) {
            int status = statusById.getOrDefault(b.getId(), 0);
            if (status == 1) {
                ok++;
            } else if (status == 2) {
                dead++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", b.getId());
                item.put("title", b.getTitle());
                item.put("url", b.getUrl());
                deadList.add(item);
            } else {
                error++;
            }
            // 仅携带 id + 需要更新的两列，避免整行回写覆盖并发修改
            Bookmark patch = new Bookmark();
            patch.setId(b.getId());
            patch.setCheckStatus(status);
            patch.setCheckedAt(now);
            patches.add(patch);
        }

        // 3) 批量写库（一次 flush，替代循环内 N 次 updateById）
        persistCheckStatus(patches);

        cacheService.evictByPrefix(CacheKey.bookmarkPrefix(userId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", bookmarks.size());
        result.put("ok", ok);
        result.put("dead", dead);
        result.put("error", error);
        result.put("deadList", deadList);
        return result;
    }

    /**
     * 并行探测全部收藏，返回 id → status(1/2/0) 映射。
     * 使用有界线程池 + 整批超时，任何单条异常都只影响该条（降级为 0），不会让接口 500。
     * 线程池在 finally 中 shutdown，避免线程泄漏。
     */
    private Map<Long, Integer> probeAll(List<Bookmark> bookmarks) {
        Map<Long, Integer> statusById = new LinkedHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(PROBE_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "dead-link-probe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Callable<ProbeResult>> tasks = new ArrayList<>(bookmarks.size());
            for (Bookmark bookmark : bookmarks) {
                final Long id = bookmark.getId();
                final String url = bookmark.getUrl();
                tasks.add(() -> new ProbeResult(id, probeUrl(url)));
            }
            // invokeAll 带超时：到期未完成的任务会被取消，保证整体耗时可控
            List<Future<ProbeResult>> futures =
                    pool.invokeAll(tasks, PROBE_BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (Future<ProbeResult> future : futures) {
                try {
                    ProbeResult probeResult = future.get();
                    if (probeResult != null && probeResult.id() != null) {
                        statusById.put(probeResult.id(), probeResult.status());
                    }
                } catch (CancellationException e) {
                    // 整批超时被取消 → 该条无法判断，保持默认 0
                    log.debug("收藏链接探测被取消（超出整批时间预算）");
                } catch (ExecutionException e) {
                    log.debug("收藏链接探测任务异常: {}", e.getCause() == null ? "unknown" : e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("收藏失效检测被中断，已探测 {} 条", statusById.size());
        } finally {
            pool.shutdownNow();
        }
        return statusById;
    }

    /** 批量写回检测状态；批量失败时降级为逐条更新，保证状态尽量落库且不抛给上层 */
    private void persistCheckStatus(List<Bookmark> patches) {
        if (patches.isEmpty()) return;
        try {
            updateBatchById(patches);
        } catch (Exception e) {
            log.warn("批量写入收藏检测状态失败，降级逐条更新: {}", e.getMessage());
            for (Bookmark patch : patches) {
                try {
                    updateById(patch);
                } catch (Exception ignored) {
                    log.debug("单条写入收藏检测状态失败 id={}", patch.getId());
                }
            }
        }
    }

    /** 单条探测结果（worker 线程返回值，不含实体引用，避免跨线程写库） */
    private record ProbeResult(Long id, int status) {
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
     * 探测单个 URL：返回 1=正常 2=失效 0=无法判断。
     *
     * <p><b>原则：宁可漏报，不可误删。</b>前端「一键清理」对 check_status=2 是
     * {@code removeById} 永久删除（不进回收站），所以判定 2 必须是「确定性的死亡信号」。</p>
     *
     * <p>判定表（任一分支都不允许叠加第二次超时，否则单条会翻倍到 10s）：</p>
     * <ul>
     *   <li>HEAD 2xx/3xx → 1</li>
     *   <li>HEAD 403/405/501 → 回退 GET 复核（不支持 HEAD 或对 HEAD 反爬）：
     *       2xx/3xx → 1，其余交给同一套状态码判定</li>
     *   <li>状态码 → 只有 <b>404 / 410</b> 判 2（页面确定不存在）；
     *       401（需登录）、408（超时）、429（限流）、451（法律屏蔽）、5xx（维护/网关抖动）
     *       一律 0 —— 这些站点都是「活着但暂时问不出来」，判 2 会被永久删除</li>
     *   <li>HEAD 抛异常：DNS 解析失败 / 连接被拒 / 路由不可达 → 2（主机已消失，真死链）；
     *       其余（读超时、SSL 握手失败等）→ 0</li>
     *   <li>非 http(s) 协议（mailto: / obsidian: / ftp: / file: / tel: …）→ 0，不探测</li>
     * </ul>
     *
     * <p><b>已知限制</b>：回环/私网地址（{@code localhost:3000}、{@code 192.168.x.x}）会被
     * 正常探测，本地服务未启动时判 2。严格说服务端探测回环地址意义有限（探测的是服务器自己的
     * 网络），但这类地址是否该一律「无法判断」取决于部署形态，且会与本地桩测试冲突，暂不处理。</p>
     */
    private int probeUrl(String url) {
        if (url == null || url.isBlank()) return 0;
        // 归一化探测目标：非 http(s) 协议直接放弃（返回 null），裸 host[:port] 补 http:// 前缀
        String target = normalizeProbeTarget(url.trim());
        if (target == null) {
            log.debug("收藏链接协议非 http(s)，跳过探测: {}", url);
            return 0;
        }
        try {
            int code;
            try (HttpResponse resp = HttpRequest.head(target)
                    .setFollowRedirects(true)
                    .timeout(PROBE_TIMEOUT_MS)
                    .header("User-Agent", UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .execute()) {
                code = resp.getStatus();
            } catch (Exception e) {
                // 只探测一次：能确定「主机已消失」的才判失效，其余一律无法判断
                log.debug("收藏链接 HEAD 探测异常 {}: {}", target, e.getMessage());
                return isHostGone(e) ? 2 : 0;
            }

            // 只有服务端不支持 HEAD（403/405/501）时才用 GET 复核
            if (code == 403 || code == 405 || code == 501) {
                try (HttpResponse resp = HttpRequest.get(target)
                        .setFollowRedirects(true)
                        .timeout(PROBE_TIMEOUT_MS)
                        .header("User-Agent", UA)
                        .header("Accept", "*/*")
                        .execute()) {
                    code = resp.getStatus();
                } catch (Exception e) {
                    log.debug("收藏链接 GET 回退探测失败 {}: {}", target, e.getMessage());
                    return 0;
                }
            }

            int verdict = judgeHttpStatus(code);
            if (verdict == 2) {
                log.debug("收藏链接判定失效 {}: HTTP {}（页面确定不存在）", target, code);
            } else if (verdict == 0) {
                log.debug("收藏链接无法判断 {}: HTTP {}（活着但问不出来）", target, code);
            }
            return verdict;
        } catch (Exception e) {
            log.debug("收藏链接探测失败 {}: {}", target, e.getMessage());
            return isHostGone(e) ? 2 : 0;
        }
    }

    /**
     * HTTP 状态码 → 判定结果。
     *
     * <p>只有 <b>404 / 410</b> 是「页面确定不存在」的确定性信号，才允许判 2。
     * 其余 4xx/5xx 全部退化为 0：401 需登录、408 超时、429 限流、451 法律屏蔽、
     * 5xx 维护或网关抖动——站点都还活着，判 2 会被「一键清理」永久删除。</p>
     */
    private static int judgeHttpStatus(int code) {
        if (code >= 200 && code < 400) return 1;
        if (code == 404 || code == 410) return 2;
        return 0;
    }

    /** RFC 3986 scheme 前缀：必须出现在串首，由字母开头 + 字母数字和 + - . 组成 */
    private static final Pattern URL_SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*):");

    /**
     * 归一化探测目标。
     *
     * <p>返回 null 表示「不该探测」（非 http/https 协议）。</p>
     *
     * <p>必须用 scheme 正则而非 {@code indexOf("://")}，否则：</p>
     * <ul>
     *   <li>{@code mailto:a@b.com}（scheme: 无 //）漏网 → Hutool 把 "mailto" 当主机名做 DNS
     *       → UnknownHostException → 误判死链 → 被永久删除</li>
     *   <li>{@code example.com/path?u=http://foo} 的 {@code ://} 在 query 里 → 误取
     *       scheme="example.com/path?u=http" → 永久跳过探测，永远卡在「无法判断」</li>
     *   <li>{@code localhost:3000} / {@code example.com:8080} 会被当成 scheme="localhost"
     *       → 误判死链</li>
     * </ul>
     */
    private static String normalizeProbeTarget(String url) {
        // 协议相对 URL（//example.com/x）补 http: 而非 http://，避免出现 http:////example.com
        if (url.startsWith("//")) return "http:" + url;

        Matcher matcher = URL_SCHEME.matcher(url);
        if (matcher.find()) {
            String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
            // scheme 冒号后紧跟数字 → 是 host:port（localhost:3000），不是协议
            boolean portLike = url.length() > matcher.end()
                    && Character.isDigit(url.charAt(matcher.end()));
            if (!portLike) {
                if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
                return url;
            }
        }
        // 裸 host[:port]/path（无协议）补默认协议，让 Hutool 按主机而非协议解析
        return "http://" + url;
    }

    /**
     * 判断异常链上是否存在「主机已消失」类异常（DNS 解析失败 / 连接被拒 / 路由不可达）。
     * 必须遍历 cause 链：Hutool 会把底层 IOException 包进 {@code HttpException} /
     * {@code IORuntimeException}，只 catch 顶层异常会漏判，导致真死链退化成「无法判断」。
     *
     * <p><b>前提</b>：服务端直连、不经过 HTTP 代理。{@code ConnectException} 的语义比
     * "Connection refused" 宽，代理拒绝/防火墙 RST 也可能抛它；若将来给探测配置代理，
     * 需收窄到 message 含 "refused"，否则代理故障会让全站被判死链并批量误删。</p>
     */
    private static boolean isHostGone(Throwable error) {
        Throwable current = error;
        // 防御异常链自引用/过深（生产上见过 cause 环）
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return false;
    }
}
