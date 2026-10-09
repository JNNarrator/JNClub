package com.jnclub.music.track.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.CacheService;
import com.jnclub.music.common.PageResponse;
import com.jnclub.music.common.enums.ErrorCode;
import com.jnclub.music.common.exception.BusinessException;
import com.jnclub.music.lanzou.LanzouApiClient;
import com.jnclub.music.lanzou.LanzouSessionException;
import com.jnclub.music.storage.MusicStorage;
import com.jnclub.music.track.mapper.TrackMapper;
import com.jnclub.music.track.domain.Track;
import com.jnclub.music.track.domain.LyricsCache;
import com.jnclub.music.track.mapper.LyricsCacheMapper;
import com.jnclub.music.storage.StorageFile;
import com.jnclub.music.storage.StorageFolder;
import com.jnclub.music.storage.StorageListResult;
import com.jnclub.music.track.dto.MediaUrlDTO;
import com.jnclub.music.track.dto.TrackDTO;
import com.jnclub.music.track.dto.TrackSummaryDTO;
import com.jnclub.music.track.dto.TrackWithUrlDTO;
import com.jnclub.music.track.service.TrackService;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TrackServiceImpl implements TrackService {

    private record SongFolder(String folderId, String folderName, StorageFile audioFile, StorageFile lyricFile) {
        ParsedName parseFolderName() {
            if (folderName == null || folderName.isBlank()) {
                return new ParsedName("", null, "", false);
            }
            String text = folderName.trim();
            int dash = text.indexOf('-');
            if (dash > 0) {
                String artist = splitCamelCase(text.substring(0, dash).trim());
                String name = splitCamelCase(text.substring(dash + 1).trim());
                if (!artist.isEmpty() && !name.isEmpty()) {
                    return new ParsedName(name, artist, audioFile != null ? getExtension(audioFile.name()) : "", false);
                }
            }
            return new ParsedName(splitCamelCase(text), null, audioFile != null ? getExtension(audioFile.name()) : "", false);
        }

        private static String splitCamelCase(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(s.charAt(i - 1))) {
                    sb.append(' ');
                }
                sb.append(c);
            }
            return sb.toString();
        }

        private static String getExtension(String fileName) {
            if (fileName == null) return "";
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TrackServiceImpl.class);
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "flac", "mp3", "wav", "aac", "m4a", "ogg", "opus", "ape"
    );
    private static final String ROOT_FOLDER_ID = "-1";
    private static final int MAX_PAGES = 20;
    /** 批量直链 Redis 缓存 TTL：对齐蓝奏云直链约 45 分钟有效期（留 30 秒裕量） */
    private static final Duration MUSIC_URLS_TTL = Duration.ofMinutes(30);
    /**
     * 直链安全余量：剩余有效期低于该值即视为「需要续期」，不再直接交给播放器。
     * <p>蓝奏云直链约 45 分钟有效，而无损曲目单曲播放就要数分钟；若把即将失效的链接返回，
     * 会出现「点了能播、播到一半断掉」。宁可回源换一条新链。</p>
     */
    private static final Duration URL_SAFETY_MARGIN = Duration.ofMinutes(5);
    /** Caffeine 缓存名（与 @CacheEvict 注解值保持一致，手动读写同源缓存） */
    private static final String CACHE_SONG_FOLDERS = "songFolders";
    private static final String CACHE_TRACK_SUMMARIES = "trackSummaries";
    private static final String CACHE_KEY_ALL = "all";
    /** 目录扫描并发度：过大易触发蓝奏云反爬，过小则首次加载阻塞过久。 */
    /**
     * 目录扫描失败（蓝奏云会话失效）后的冷却截止时间。
     * <p>空结果本身不写缓存，若不设冷却，未认证状态下每个请求都会重跑一次全量扫树。</p>
     */
    private volatile long folderScanBlockedUntil = 0;
    /** 会话失效后的扫树冷却窗口。 */
    private static final long FOLDER_SCAN_FAILURE_COOLDOWN_MS = 30_000L;
    private static final int FOLDER_SCAN_CONCURRENCY = 4;
    /**
     * 单层扫描的等待上限。
     * <p>依据：单个子文件夹的扫描是「第 1 页 listFiles」，而单次 listFiles 内部有 2 次 douploadPost，
     * 每次最多 3 轮反爬重试（含 800ms/2000ms 退避）+ OkHttp 读超时 30s，最坏约 90s；正常仅 1~3s。
     * 取 60s 既能给出上界，又不会把偶发的慢响应误判为失败。</p>
     */
    private static final long FOLDER_SCAN_LEVEL_TIMEOUT_MS = 60_000L;
    /** 整轮扫描的总预算：超过即视为扫描失败（不缓存部分结果），并进入冷却。 */
    private static final long FOLDER_SCAN_TOTAL_TIMEOUT_MS = 120_000L;
    /** 单飞锁条带数：定长数组，按 trackId 哈希取模，无需清理。 */
    private static final int MEDIA_URL_LOCK_STRIPES = 64;

    private final MusicStorage musicStorage;
    private final TrackMapper trackMapper;
    private final LyricsCacheMapper lyricsCacheMapper;
    private final CacheService cacheService;
    private final CacheManager cacheManager;

    /** 单曲「不可播」冷却：trackId -> 在此时刻前视为不可播，避免对持续坏链/坏 URL 每请求都回源蓝奏云。 */
    private static final long UNPLAYABLE_COOLDOWN_MS = 60_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> unplayableUntil =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 目录扫描专用线程池：有界并发，把「每层每页一次 HTTP」的串行扫描压缩到可接受耗时。 */
    private final ExecutorService folderScanExecutor = Executors.newFixedThreadPool(
            FOLDER_SCAN_CONCURRENCY, daemonThreadFactory("music-folder-scan-"));
    /** 取链单飞锁条带：同一 trackId 的并发取链合并为一次回源，避免重复请求放大蓝奏云反爬风险。 */
    private final Object[] mediaUrlLocks = createLockStripes();

    public TrackServiceImpl(MusicStorage musicStorage, TrackMapper trackMapper, LyricsCacheMapper lyricsCacheMapper, CacheService cacheService, CacheManager cacheManager) {
        this.musicStorage = musicStorage;
        this.trackMapper = trackMapper;
        this.lyricsCacheMapper = lyricsCacheMapper;
        this.cacheService = cacheService;
        this.cacheManager = cacheManager;
    }

    /** 目录扫描线程池使用守护线程，这里显式关闭以保证优雅停机。 */
    @PreDestroy
    void shutdownFolderScanExecutor() {
        folderScanExecutor.shutdownNow();
    }

    /** 目录全量扫描的单飞锁：同一时刻只允许一次扫树。 */
    private final Object folderScanLock = new Object();

    /** 守护线程工厂：命名 + daemon，避免线程池阻止 JVM 退出。 */
    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    /** 定长条带锁：同一 trackId 恒定映射到同一把锁，且无需清理，避免锁对象泄漏。 */
    private static Object[] createLockStripes() {
        Object[] stripes = new Object[MEDIA_URL_LOCK_STRIPES];
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new Object();
        }
        return stripes;
    }

    @Override
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize) {
        return listTracks(page, pageSize, false);
    }

    @Override
    @CacheEvict(value = {"songFolders", "trackSummaries"}, allEntries = true, condition = "#refresh")
    public PageResponse<TrackSummaryDTO> listTracks(Integer page, Integer pageSize, boolean refresh) {
        return paginate(getCachedSummaries(), normalize(page, 1), normalize(pageSize, 20));
    }

    @Override
    public PageResponse<TrackSummaryDTO> searchTracks(String keyword, Integer page, Integer pageSize) {
        String kw = trim(keyword);
        if (kw.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        String lower = kw.toLowerCase(Locale.ROOT);
        List<TrackSummaryDTO> matched = new ArrayList<>();
        for (TrackSummaryDTO t : getCachedSummaries()) {
            if (containsIgnoreCase(t.getName(), lower) || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, normalize(page, 1), normalize(pageSize, 20));
    }

    @Override
    public TrackDTO getTrackById(String trackId) {
        String id = requireTrackId(trackId);
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) {
                ParsedName pn = sf.parseFolderName();
                return TrackDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size()).hasLyric(sf.lyricFile() != null).build();
            }
        }
        throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    @Override
    public PageResponse<TrackDTO> getTracksByIds(List<String> ids) {
        // 原实现是 ids × folders 的双重循环，且内层每轮都重新调用 loadSongFolders()；
        // 批量接口最多 50 个 id，改为一趟建索引后 O(n) 查表。
        Map<String, SongFolder> byId = indexSongFoldersById();
        List<TrackDTO> items = new ArrayList<>();
        for (String id : ids) {
            SongFolder sf = byId.get(id);
            if (sf == null) continue;
            ParsedName pn = sf.parseFolderName();
            items.add(TrackDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                    .format(pn.format()).fileSize(sf.audioFile().size()).hasLyric(sf.lyricFile() != null).build());
        }
        return PageResponse.<TrackDTO>builder().items(items).page(1).pageSize(0).total((long) items.size()).hasMore(false).build();
    }

    /** 建立 audioFileId -> SongFolder 索引；同一 id 只保留首个匹配，与原实现的 break 语义一致。 */
    private Map<String, SongFolder> indexSongFoldersById() {
        Map<String, SongFolder> byId = new HashMap<>();
        for (SongFolder sf : loadSongFolders()) {
            byId.putIfAbsent(sf.audioFile().id(), sf);
        }
        return byId;
    }

    /**
     * 获取可播放直链。
     * <p>缓存策略（显式 Caffeine，避免把「不可播」的负面结果缓存住阻碍重试恢复）：
     * 只有可播放的直链才写入 mediaUrls 缓存；命中且可播则快速返回，否则回源刷新 + 健康预检。</p>
     * <p>未命中时按 trackId 加单飞锁：并发点播同一首歌只触发一次蓝奏云回源，其余请求复用结果。</p>
     */
    @Override
    public MediaUrlDTO getMediaUrl(String trackId) {
        String id = requireTrackId(trackId);
        MediaUrlDTO fast = cachedMediaUrl(id);
        if (fast != null) return fast;
        // 单飞：冷取链需 3~5 次蓝奏云 HTTP 且可能被反爬拦截，重复回源既拖慢响应又放大风控概率。
        synchronized (mediaUrlLocks[Math.floorMod(id.hashCode(), MEDIA_URL_LOCK_STRIPES)]) {
            MediaUrlDTO again = cachedMediaUrl(id);
            if (again != null) return again;
            return refreshAndCheck(id);
        }
    }

    /**
     * 只读缓存命中判定：L1（Caffeine，需可播且余量充足）→ L2（MySQL，需双重校验有效）。
     * <p>未命中返回 null，由调用方决定是否回源。</p>
     */
    private MediaUrlDTO cachedMediaUrl(String id) {
        Cache cache = cacheManager.getCache("mediaUrls");
        // 注意：必须同时校验 expiresAt —— 缓存 TTL（45min）与直链有效期（45min）几乎相同，
        // 若只判断 playable，缓存中「已失效但尚未被 Caffeine 淘汰」的链接会被原样返回，
        // 表现为歌曲播到一半中断或点了没声音。
        MediaUrlDTO hit = cacheGet(cache, id);
        if (hit != null && Boolean.TRUE.equals(hit.getPlayable()) && isFreshEnough(hit.getExpiresAt())) {
            return hit;
        }
        // L2: 再查 MySQL 缓存的直链（双重校验：数据库过期时间 + 直链 e 参数 + 健康标志）
        var cachedTrack = trackMapper.selectById(id);
        if (cachedTrack != null && isMediaUrlFresh(cachedTrack) && isPlayable(cachedTrack)) {
            String fmt = cachedTrack.getFormat() != null ? cachedTrack.getFormat() : "";
            var dto = MediaUrlDTO.builder().trackId(id).mediaUrl(cachedTrack.getMediaUrl()).format(fmt)
                    .expiresAt(cachedTrack.getUrlExpiresAt()).playable(true).build();
            putMediaUrlCache(cache, id, dto);
            return dto;
        }
        return null;
    }

    private void putMediaUrlCache(Cache cache, String id, MediaUrlDTO dto) {
        if (cache != null && Boolean.TRUE.equals(dto.getPlayable())) {
            cache.put(id, dto);
        }
    }

    /**
     * 强制回源拉直链 + 对新直链做健康预检，回写 MySQL 并返回带 playable 结果的 DTO。
     * 蓝奏云可用但个别文件 403 时，返回 playable=false（mediaUrl 保留，供前端兜底/手动重试），
     * 不抛异常，避免把「单曲坏链」误判为整个模块失败。
     */
    private MediaUrlDTO refreshAndCheck(String id) {
        // 已确认源文件永久不可用（分享取消/文件不存在）→ 直接返回，不再回源蓝奏云
        Track known = trackMapper.selectById(id);
        if (known != null && SOURCE_GONE.equals(known.getLastError())) {
            return MediaUrlDTO.builder().trackId(id).playable(false).message(SOURCE_GONE).build();
        }
        // 冷却期内：最近刚判定不可播，直接返回不可播，避免每请求都回源蓝奏云
        Long until = unplayableUntil.get(id);
        if (until != null && until > System.currentTimeMillis()) {
            return MediaUrlDTO.builder().trackId(id).playable(false).message("MEDIA_UNAVAILABLE").build();
        }
        String format = resolveFormat(id);
        try {
            var dl = musicStorage.getDownloadUrlWithExpiry(id);
            boolean ok = checkPlayable(dl.url());
            if (ok) {
                unplayableUntil.remove(id);
            } else {
                unplayableUntil.put(id, System.currentTimeMillis() + UNPLAYABLE_COOLDOWN_MS);
                if (dl.url() == null || dl.url().isBlank()) {
                    markUnplayable(id, "MEDIA_UNAVAILABLE");
                    return MediaUrlDTO.builder().trackId(id).playable(false).message("MEDIA_UNAVAILABLE").build();
                }
            }
            var dto = MediaUrlDTO.builder().trackId(id).mediaUrl(dl.url()).format(format)
                    .expiresAt(toOffsetDateTime(dl.expiresAt()))
                    .playable(ok).message(ok ? null : "MEDIA_UNAVAILABLE").build();
            persistUrl(id, dto);
            putMediaUrlCache(cacheManager.getCache("mediaUrls"), id, dto);
            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 用与全局一致的异常映射判断：会话失效是全局问题 → 抛出提示重新认证；
            // 其余单曲取链失败 → 返回不可播标志让前端快速跳过，不抛 500。
            BusinessException be = toLanzouBusinessException(e, "获取播放链接失败");
            if (be.getErrorCode() == ErrorCode.LANZOU_SESSION_EXPIRED) {
                unplayableUntil.remove(id);
                throw be;
            }
            if (isPermanentSourceGone(e)) {
                // 分享已取消 / 文件不存在：重试无意义，清空死链并打标，后续续期任务跳过
                log.warn("单曲源文件已不可用 trackId={}: {}", id, e.getMessage());
                markSourceGone(id);
                return MediaUrlDTO.builder().trackId(id).playable(false).message(SOURCE_GONE).build();
            }
            log.warn("单曲直链不可用 trackId={}: {}", id, e.getMessage());
            markUnplayable(id, "MEDIA_UNAVAILABLE");
            unplayableUntil.put(id, System.currentTimeMillis() + UNPLAYABLE_COOLDOWN_MS);
            return MediaUrlDTO.builder().trackId(id).playable(false).message("MEDIA_UNAVAILABLE").build();
        }
    }

    /**
     * 回写 MySQL：更新直链、过期时间、可播放标志与失败原因。
     * <p>改为定向 UPDATE（而非 selectById + updateById 的全字段读改写）：
     * 既省掉一次 SELECT，也避免用陈旧快照覆盖并发写入的其他字段（如另一线程刚续期的直链）。</p>
     */
    private void persistUrl(String id, MediaUrlDTO dto) {
        var update = com.baomidou.mybatisplus.core.toolkit.Wrappers.<Track>lambdaUpdate()
                .eq(Track::getTrackId, id)
                .set(Track::getMediaUrl, dto.getMediaUrl())
                .set(Track::getUrlExpiresAt, dto.getExpiresAt())
                .set(Track::getPlayable, Boolean.TRUE.equals(dto.getPlayable()) ? 1 : 0)
                .set(Track::getLastError, dto.getMessage());
        // format 为派生信息，取链结果里可能缺失，缺失时不要覆盖库里已有的值
        if (dto.getFormat() != null) {
            update.set(Track::getFormat, dto.getFormat());
        }
        trackMapper.update(null, update);
    }

    /** 记录单曲不可用（保留旧 URL 兜底，仅更新标志）。 */
    private void markUnplayable(String id, String reason) {
        try {
            trackMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Track>lambdaUpdate()
                    .eq(Track::getTrackId, id)
                    .set(Track::getPlayable, 0)
                    .set(Track::getLastError, reason));
        } catch (Exception ignore) { /* 记录失败不影响主流程 */ }
    }

    /**
     * 标记源文件永久不可用：清空死链并写入 {@link #SOURCE_GONE}。
     * <p>清空 mediaUrl 可避免继续把死链暴露给前端；打标后续期任务会跳过该曲目，
     * 不再每轮都回源重试。若用户重新分享，可通过管理端手动全量刷新恢复。</p>
     */
    private void markSourceGone(String id) {
        try {
            trackMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Track>lambdaUpdate()
                    .eq(Track::getTrackId, id)
                    .set(Track::getPlayable, 0)
                    .set(Track::getLastError, SOURCE_GONE)
                    .set(Track::getMediaUrl, null)
                    .set(Track::getUrlExpiresAt, null));
        } catch (Exception ignore) { /* 记录失败不影响主流程 */ }
    }

    /** 是否为「源文件永久不可用」类错误（分享被取消 / 文件不存在）——此类重试无意义。 */
    private static boolean isPermanentSourceGone(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String m = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
            if (m.contains("share cancelled") || m.contains("file not exist")
                    || m.contains("取消分享") || m.contains("文件不存在")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /** 缓存条目是否可用：fresh 且上次预检为可播放（playable 缺省视为可播放，兼容旧数据）。 */
    private static boolean isPlayable(Track t) {
        return t.getPlayable() == null || t.getPlayable() == 1;
    }

    @Override
    @CacheEvict(value = "mediaUrls", key = "#trackId")
    public MediaUrlDTO refreshMediaUrl(String trackId) {
        String id = requireTrackId(trackId);
        try {
            // 强制调蓝奏云拉取新直链，不走缓存
            var dl = musicStorage.getDownloadUrlWithExpiry(id);
            boolean ok = checkPlayable(dl.url());
            // 注意：必须带上 format。此前该 DTO 未设置 format，回写时会把库里的格式清成 null。
            var dto = MediaUrlDTO.builder().trackId(id)
                    .mediaUrl(dl.url())
                    .format(resolveFormat(id))
                    .expiresAt(toOffsetDateTime(dl.expiresAt()))
                    .playable(ok).message(ok ? null : "MEDIA_UNAVAILABLE").build();
            persistUrl(id, dto);
            return dto;
        } catch (Exception e) {
            log.warn("强制刷新直链失败 trackId={}: {}", id, e.getMessage());
            if (isPermanentSourceGone(e)) {
                // 分享已取消/文件不存在：打标清链，让续期任务跳过，避免每轮无谓回源与告警
                markSourceGone(id);
            }
            throw toLanzouBusinessException(e, "刷新直链失败");
        }
    }

    @Override
    public Map<String, MediaUrlDTO> getMediaUrls(List<String> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) return Map.of();
        Map<String, MediaUrlDTO> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        // L2: 先查 MySQL — 一次性批量查询
        List<Track> cached = trackMapper.selectBatchIds(trackIds);
        Map<String, Track> cacheMap = new HashMap<>();
        for (Track ct : cached) {
            cacheMap.put(ct.getTrackId(), ct);
        }
        for (String id : trackIds) {
            Track ct = cacheMap.get(id);
            if (ct != null && isMediaUrlFresh(ct)) {
                String fmt = ct.getFormat() != null ? ct.getFormat() : "";
                result.put(id, MediaUrlDTO.builder().trackId(id).mediaUrl(ct.getMediaUrl()).format(fmt)
                        .expiresAt(ct.getUrlExpiresAt()).playable(isPlayable(ct)).build());
            } else {
                missing.add(id);
            }
        }
        // 未命中 → 调蓝奏云批量获取
        if (!missing.isEmpty()) {
            Map<String, com.jnclub.music.storage.DownloadUrl> urlMap = musicStorage.getDownloadUrlsWithExpiry(missing);
            if (!urlMap.isEmpty()) {
                // 原实现遍历整个目录树去匹配 urlMap，是 folders × missing 的双重循环；改为按 id 建索引后直接查。
                Map<String, SongFolder> byId = indexSongFoldersById();
                for (Map.Entry<String, com.jnclub.music.storage.DownloadUrl> e : urlMap.entrySet()) {
                    String id = e.getKey();
                    var dl = e.getValue();
                    SongFolder sf = byId.get(id);
                    if (dl == null || sf == null) continue;
                    var dto = MediaUrlDTO.builder().trackId(id).mediaUrl(dl.url())
                            .format(sf.parseFolderName().format()).expiresAt(toOffsetDateTime(dl.expiresAt()))
                            .playable(true).build();
                    result.put(id, dto);
                    // 回写 MySQL — 从 cacheMap 复用避免二次查询
                    Track ct = cacheMap.get(id);
                    if (ct != null) {
                        ct.setMediaUrl(dto.getMediaUrl());
                        ct.setUrlExpiresAt(dto.getExpiresAt());
                        ct.setPlayable(1);
                        ct.setLastError(null);
                        trackMapper.updateById(ct);
                    }
                }
            }
        }
        return result;
    }

    @Override
    @Cacheable(value = "lyrics", key = "#trackId")
    public String getLyrics(String trackId) {
        String id = requireTrackId(trackId);
        // L2: 先查 MySQL 歌词缓存
        LyricsCache cached = lyricsCacheMapper.selectById(id);
        if (cached != null && cached.getLyrics() != null && !cached.getLyrics().isBlank()) {
            return cached.getLyrics();
        }
        for (SongFolder sf : loadSongFolders()) {
            if (id.equals(sf.audioFile().id())) {
                if (sf.lyricFile() == null) break;
                String lyrics = downloadText(musicStorage.getDownloadUrl(sf.lyricFile().id()));
                // 回写 L2 (MySQL)
                try {
                    LyricsCache existing = lyricsCacheMapper.selectById(id);
                    if (existing != null) {
                        existing.setLyrics(lyrics);
                        lyricsCacheMapper.updateById(existing);
                    } else {
                        LyricsCache lc = LyricsCache.builder().trackId(id).lyrics(lyrics).build();
                        lyricsCacheMapper.insert(lc);
                    }
                } catch (Exception e) {
                    log.warn("歌词缓存写入失败 trackId={}: {}", id, e.getMessage());
                }
                return lyrics;
            }
        }
        throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    @Override
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize) {
        return listTracksWithUrl(page, pageSize, false);
    }

    @Override
    @CacheEvict(value = "songFolders", allEntries = true, condition = "#refresh")
    public PageResponse<TrackWithUrlDTO> listTracksWithUrl(Integer page, Integer pageSize, boolean refresh) {
        return paginate(loadAllAudioWithUrl(), normalize(page, 1), normalize(pageSize, 20));
    }

    @Override
    public PageResponse<TrackWithUrlDTO> searchTracksWithUrl(String keyword, Integer page, Integer pageSize) {
        String kw = trim(keyword);
        if (kw.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "搜索关键词不能为空");
        String lower = kw.toLowerCase(Locale.ROOT);
        List<TrackWithUrlDTO> matched = new ArrayList<>();
        for (TrackWithUrlDTO t : loadAllAudioWithUrl()) {
            if (containsIgnoreCase(t.getName(), lower) || containsIgnoreCase(t.getArtist(), lower)) {
                matched.add(t);
            }
        }
        return paginate(matched, normalize(page, 1), normalize(pageSize, 20));
    }

    /**
     * 加载蓝奏云目录树（songFolders 缓存）。
     * <p>注意：不能依赖 {@code @Cacheable}——本方法在同类内部被多处 self-invocation 调用，
     * 不会经过 Spring AOP 代理，注解缓存失效。改为显式读/写 Caffeine，与 {@code @CacheEvict} 同源。
     * 空结果（蓝奏云会话失效）不缓存，避免把临时故障锁死 45 分钟。</p>
     */
    public List<SongFolder> loadSongFolders() {
        Cache cache = cacheManager.getCache(CACHE_SONG_FOLDERS);
        List<SongFolder> hit = cacheGet(cache, CACHE_KEY_ALL);
        if (hit != null) return hit;

        // 单飞：Caffeine 不做 read-through，缓存过期瞬间若并发请求各自扫树，
        // 会对蓝奏云发起成倍请求（每次扫描是「每层每页一次 + 每个文件夹一次」HTTP），
        // 既拖慢首屏也极易触发反爬。这里让并发未命中只跑一次全量扫描。
        synchronized (folderScanLock) {
            List<SongFolder> second = cacheGet(cache, CACHE_KEY_ALL);
            if (second != null) return second;
            // 会话失效冷却期内直接返回空：空结果不写缓存，若不加冷却，
            // 未认证状态下每个列表/搜索/取链请求都会重跑一次全量扫树，
            // 请求量被放大成「请求数 × 目录规模」，持续冲击蓝奏云反爬风控。
            if (System.currentTimeMillis() < folderScanBlockedUntil) {
                return new ArrayList<>();
            }
            List<SongFolder> folders = scanAllSongFolders();
            if (!folders.isEmpty() && cache != null) {
                cache.put(CACHE_KEY_ALL, folders);
            }
            return folders;
        }
    }

    /**
     * 全量扫描目录树。
     * <p>两类失败都按「扫描失败」处理（返回空列表、不写缓存、进入短冷却），不向接口抛 500：
     * 蓝奏云会话失效，以及扫描超时（网络挂起/反爬退避）。其余异常原样抛出。</p>
     * <p>关键约束：任何失败路径都不得把已扫到的部分结果写入缓存——否则曲库会以不完整状态被缓存 45 分钟。</p>
     */
    private List<SongFolder> scanAllSongFolders() {
        try {
            List<SongFolder> folders = new ArrayList<>();
            loadSongFoldersRecursively(ROOT_FOLDER_ID, folders);
            folderScanBlockedUntil = 0;
            return folders;
        } catch (Exception e) {
            // 并发扫描会把底层异常包进 CompletionException，先解包再判定类型。
            Throwable root = rootCauseOf(e);
            if (root instanceof LanzouSessionException) {
                // 蓝奏云会话失效：返回空列表由上层展示/引导重新认证，
                // 同时保留 Caffeine 中可能仍存在的旧缓存值供播放直链兜底。
                // 冷却窗口取 30s：既能挡住放大效应，又不至于让管理员重新认证后长时间看不到曲目。
                folderScanBlockedUntil = System.currentTimeMillis() + FOLDER_SCAN_FAILURE_COOLDOWN_MS;
                log.warn("loadSongFolders: 蓝奏云会话失效，返回空列表并进入 {}s 冷却，请重新认证。原因: {}",
                        FOLDER_SCAN_FAILURE_COOLDOWN_MS / 1000, root.getMessage());
                return new ArrayList<>();
            }
            if (root instanceof FolderScanTimeoutException) {
                // 扫描超时同样进入冷却，避免卡住时后续请求立刻再来一轮全量扫描
                folderScanBlockedUntil = System.currentTimeMillis() + FOLDER_SCAN_FAILURE_COOLDOWN_MS;
                log.warn("loadSongFolders: 目录扫描超时，返回空列表并进入 {}s 冷却，保留旧缓存兜底。原因: {}",
                        FOLDER_SCAN_FAILURE_COOLDOWN_MS / 1000, root.getMessage());
                return new ArrayList<>();
            }
            if (root instanceof RuntimeException re) throw re;
            throw new IllegalStateException(root);
        }
    }

    /** 从 Caffeine 缓存按 key 读值；未命中返回 null（泛型强转，存入与读取同类型，安全）。 */
    @SuppressWarnings("unchecked")
    private static <T> T cacheGet(Cache cache, String key) {
        if (cache == null) return null;
        Cache.ValueWrapper vw = cache.get(key);
        return vw == null ? null : (T) vw.get();
    }

    /**
     * 扫描目录树，收集歌曲文件夹。
     * <p>扫描成本是「每层每页一次 HTTP + 每个子文件夹一次 HTTP」，原实现完全串行，
     * 首次加载或 songFolders 缓存过期后会把请求阻塞数十秒。现改为按层并发：
     * 层内并发（{@link #FOLDER_SCAN_CONCURRENCY}）、层间串行，任务不会向同一线程池嵌套提交，
     * 因此不会出现线程池饥饿（提交的任务等待自己所在池的线程）而死锁。</p>
     */
    private void loadSongFoldersRecursively(String folderId, List<SongFolder> out) {
        long deadline = System.currentTimeMillis() + FOLDER_SCAN_TOTAL_TIMEOUT_MS;
        List<StorageFolder> level = listChildFolders(folderId);
        while (!level.isEmpty()) {
            List<CompletableFuture<LevelScan>> futures = new ArrayList<>(level.size());
            for (StorageFolder f : level) {
                futures.add(CompletableFuture.supplyAsync(() -> scanLevelItem(f), folderScanExecutor));
            }
            List<StorageFolder> next = new ArrayList<>();
            for (CompletableFuture<LevelScan> fu : futures) {
                // 按 futures 顺序取结果，保证输出顺序与串行扫描一致
                LevelScan r = awaitLevelScan(fu, deadline);
                if (r.song() != null) out.add(r.song());
                else next.addAll(r.childFolders());
            }
            level = next;
        }
    }

    /**
     * 等待单个目录扫描任务，带「单层上限 + 整轮预算」双重超时。
     * <p>此前用无超时的 {@code join()}：单个任务卡住（网络挂起 / 反爬退避）会让调用线程无限等待，
     * 而所有走 {@code loadSongFolders} 的接口（列表、搜索、取链时的 format 解析、歌词）都会被一起阻塞。</p>
     *
     * @throws FolderScanTimeoutException 超时（交由 {@code scanAllSongFolders} 统一按扫描失败处理）
     */
    private LevelScan awaitLevelScan(CompletableFuture<LevelScan> future, long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        long waitMs = Math.min(FOLDER_SCAN_LEVEL_TIMEOUT_MS, remaining);
        if (waitMs <= 0) {
            throw new FolderScanTimeoutException(
                    "目录扫描已超出整轮预算 " + FOLDER_SCAN_TOTAL_TIMEOUT_MS + "ms");
        }
        try {
            return future.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new FolderScanTimeoutException("目录扫描单层等待超过 " + waitMs + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FolderScanTimeoutException("目录扫描被中断", e);
        } catch (ExecutionException e) {
            // 解包真实原因（例如 LanzouSessionException），保持与串行实现一致的异常语义
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new FolderScanTimeoutException("目录扫描任务失败", cause == null ? e : cause);
        }
    }

    /**
     * 目录扫描超时：属可重试的临时失败。
     * <p>不作为 500 抛出——与「会话失效」同样按扫描失败处理（返回空列表、不写缓存、进入冷却），
     * 避免一次网络抖动让整个音乐模块的列表/搜索接口报错。</p>
     */
    private static final class FolderScanTimeoutException extends RuntimeException {
        FolderScanTimeoutException(String message) { super(message); }
        FolderScanTimeoutException(String message, Throwable cause) { super(message, cause); }
    }

    /** 分页列出目录下的子文件夹（忽略散落文件，与原实现一致）。 */
    private List<StorageFolder> listChildFolders(String folderId) {
        List<StorageFolder> folders = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            StorageListResult r = musicStorage.listFiles(folderId, page);
            if (r == null || (r.files().isEmpty() && r.folders().isEmpty())) break;
            folders.addAll(r.folders());
        }
        return folders;
    }

    /**
     * 扫描单个子文件夹：第 1 页能找到音频文件即视为歌曲文件夹（与原 {@code scanSongFolder} 判定一致）；
     * 否则当作目录，返回其全部子文件夹供下一层继续扫描（复用已取到的第 1 页结果，少一次请求）。
     */
    private LevelScan scanLevelItem(StorageFolder folder) {
        StorageListResult first = musicStorage.listFiles(folder.id(), 1);
        if (first != null && !first.files().isEmpty()) {
            StorageFile audioFile = null, lyricFile = null;
            for (StorageFile f : first.files()) {
                if (isAudio(f.name())) audioFile = f;
                else if (f.name().toLowerCase(Locale.ROOT).endsWith(".txt")) lyricFile = f;
            }
            if (audioFile != null) {
                return new LevelScan(new SongFolder(folder.id(), folder.name(), audioFile, lyricFile), List.of());
            }
        }
        List<StorageFolder> children = first == null ? new ArrayList<>() : new ArrayList<>(first.folders());
        for (int page = 2; page <= MAX_PAGES; page++) {
            StorageListResult r = musicStorage.listFiles(folder.id(), page);
            if (r == null || (r.files().isEmpty() && r.folders().isEmpty())) break;
            children.addAll(r.folders());
        }
        return new LevelScan(null, children);
    }

    /** 单层扫描结果：要么是歌曲文件夹，要么是需要继续下探的子文件夹列表。 */
    private record LevelScan(SongFolder song, List<StorageFolder> childFolders) {}

    /** 解包 CompletableFuture/包装异常，取出真正的根因。 */
    private static Throwable rootCauseOf(Throwable e) {
        Throwable root = e;
        while ((root instanceof java.util.concurrent.CompletionException
                || root instanceof java.util.concurrent.ExecutionException)
                && root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * 歌曲摘要列表（trackSummaries 缓存）。同样避免 {@code @Cacheable} 同类内部调用失效，
     * 改为显式 Caffeine 读写；空结果不缓存。
     */
    public List<TrackSummaryDTO> getCachedSummaries() {
        Cache cache = cacheManager.getCache(CACHE_TRACK_SUMMARIES);
        List<TrackSummaryDTO> hit = cacheGet(cache, CACHE_KEY_ALL);
        // 缓存存活期（45min）与直链有效期（45min）相当，命中时链接可能已失效，
        // 因此返回前再剔除一次，保证交给前端的 mediaUrl 始终可用
        // （否则前端会误判「已取到链接」而跳过预取，切歌时才开始现取）。
        if (hit != null) return stripStaleUrls(hit);

        List<TrackSummaryDTO> out = loadAllAudioSummaries();
        if (!out.isEmpty() && cache != null) {
            cache.put(CACHE_KEY_ALL, out);
        }
        return out;
    }

    /**
     * 剔除已失效或余量不足的直链（置空 mediaUrl / urlExpiresAt）。
     * <p>不修改入参对象，避免污染 Caffeine 中的缓存条目。</p>
     */
    private static List<TrackSummaryDTO> stripStaleUrls(List<TrackSummaryDTO> src) {
        OffsetDateTime threshold = OffsetDateTime.now().plus(URL_SAFETY_MARGIN);
        // 绝大多数曲目的直链余量充足，先探测一次：无失效项就直接复用缓存列表，
        // 避免每次列表请求都重建 N 个 DTO（列表是高频接口）。
        boolean hasStale = false;
        for (TrackSummaryDTO t : src) {
            if (t.getMediaUrl() != null
                    && (t.getUrlExpiresAt() == null || !t.getUrlExpiresAt().isAfter(threshold))) {
                hasStale = true;
                break;
            }
        }
        if (!hasStale) return src;

        List<TrackSummaryDTO> out = new ArrayList<>(src.size());
        for (TrackSummaryDTO t : src) {
            if (t.getMediaUrl() != null && t.getUrlExpiresAt() != null
                    && t.getUrlExpiresAt().isAfter(threshold)) {
                out.add(t);
            } else {
                out.add(TrackSummaryDTO.builder()
                        .trackId(t.getTrackId()).name(t.getName()).artist(t.getArtist())
                        .album(t.getAlbum()).coverUrl(t.getCoverUrl()).duration(t.getDuration())
                        .format(t.getFormat()).fileSize(t.getFileSize()).hasLyric(t.getHasLyric())
                        .mediaUrl(null).urlExpiresAt(null)
                        .build());
            }
        }
        return out;
    }


    private List<TrackSummaryDTO> loadAllAudioSummaries() {
        // 先从 MySQL 加载所有缓存的直链
        java.util.Map<String, Track> cacheMap = new java.util.HashMap<>();
        for (com.jnclub.music.track.domain.Track t : trackMapper.selectList(null)) {
            if (t.getMediaUrl() != null && !t.getMediaUrl().isBlank()) {
                cacheMap.put(t.getTrackId(), t);
            }
        }
        List<TrackSummaryDTO> out = new ArrayList<>();
        for (SongFolder sf : loadSongFolders()) {
            ParsedName pn = sf.parseFolderName();
            var cached = cacheMap.get(sf.audioFile().id());
            // 只把「仍有余量」的直链带给前端：过期死链会让前端误判为已取到链接而跳过预取
            // （prefetchNextUrls 仅对 mediaUrl 为空的曲目取链），反而让切歌时才开始现取、等待变长。
            boolean usable = cached != null && isMediaUrlFresh(cached);
            out.add(TrackSummaryDTO.builder().trackId(sf.audioFile().id()).name(pn.name()).artist(pn.artist())
                    .format(pn.format()).fileSize(sf.audioFile().size()).hasLyric(sf.lyricFile() != null)
                    .mediaUrl(usable ? cached.getMediaUrl() : null)
                    .urlExpiresAt(usable ? cached.getUrlExpiresAt() : null)
                    .build());
        }
        return out;
    }

    private List<TrackWithUrlDTO> loadAllAudioWithUrl() {
        List<SongFolder> folders = loadSongFolders();
        // 先读 Redis 批量直链（key: trackId -> {url, expiresAtMillis}），未命中/已过期才回源蓝奏云
        Map<String, JSONObject> urlMap = readMusicUrlsFromCache();
        List<TrackWithUrlDTO> out = new ArrayList<>();
        Map<String, JSONObject> toWrite = new HashMap<>();
        boolean changed = false;

        for (SongFolder sf : folders) {
            ParsedName pn = sf.parseFolderName();
            String id = sf.audioFile().id();
            JSONObject cached = urlMap.get(id);
            if (cached != null && isUrlFresh(cached)) {
                out.add(TrackWithUrlDTO.builder().trackId(id).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size())
                        .mediaUrl(cached.getStr("url"))
                        .urlExpiresAt(toOffsetDateTime(Instant.ofEpochMilli(cached.getLong("expiresAtMillis"))))
                        .build());
                continue;
            }
            try {
                var dl = musicStorage.getDownloadUrlWithExpiry(id);
                out.add(TrackWithUrlDTO.builder().trackId(id).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size()).mediaUrl(dl.url())
                        .urlExpiresAt(toOffsetDateTime(dl.expiresAt())).build());
                JSONObject entry = new JSONObject();
                entry.set("url", dl.url());
                entry.set("expiresAtMillis", dl.expiresAt() != null ? dl.expiresAt().toEpochMilli() : 0L);
                toWrite.put(id, entry);
                changed = true;
            } catch (Exception e) {
                BusinessException be = toLanzouBusinessException(e, "获取播放直链失败");
                if (be.getErrorCode() == ErrorCode.LANZOU_SESSION_EXPIRED) {
                    throw be;
                }
                log.warn("单曲直链不可用，跳过 trackId={}: {}", id, e.getMessage());
                out.add(TrackWithUrlDTO.builder().trackId(id).name(pn.name()).artist(pn.artist())
                        .format(pn.format()).fileSize(sf.audioFile().size()).build());
            }
        }

        // 回写 Redis：合并「仍有效的旧缓存项 + 本次回源的新条目」，避免整体覆盖丢缓存
        if (changed) {
            Map<String, JSONObject> merged = new HashMap<>(urlMap);
            merged.putAll(toWrite);
            cacheService.set(CacheKey.musicUrls(), JSONUtil.toJsonStr(merged), MUSIC_URLS_TTL);
        }
        return out;
    }

    /** 读 Redis 中的批量直链缓存，反序列化为 trackId -> {url, expiresAtMillis}；失败返回空 Map。 */
    private Map<String, JSONObject> readMusicUrlsFromCache() {
        String json = cacheService.get(CacheKey.musicUrls());
        if (json == null || json.isBlank()) return Map.of();
        try {
            JSONObject root = JSONUtil.parseObj(json);
            Map<String, JSONObject> map = new HashMap<>();
            for (String k : root.keySet()) {
                JSONObject jo = root.getJSONObject(k);
                if (jo != null) {
                    map.put(k, jo);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("批量直链缓存反序列化失败，忽略缓存: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 判断缓存的直链是否未过期。expiresAtMillis 为 0 时视为过期（保守回源）。 */
    private boolean isUrlFresh(JSONObject entry) {
        Long expiresAt = entry.getLong("expiresAtMillis");
        if (expiresAt == null || expiresAt == 0L) return false;
        if (expiresAt <= System.currentTimeMillis()) return false;
        // 防御：直链 e 参数是生成时间（有效期约 45 分钟），数据库/Redis 里可能存着
        // 按旧逻辑算出的未来过期时间（now+4h），这里按 e 重新校验，避免返回死链。
        String url = entry.getStr("url");
        if (url != null && !url.isBlank()) {
            return LanzouApiClient.resolveRealExpiry(url).isAfter(Instant.now());
        }
        return true;
    }

    /**
     * 判断 MySQL 缓存的直链是否仍有效。
     * <p>双重校验：①数据库过期时间；②直链 URL 的 e 参数（蓝奏云 e=链接生成时间，
     * 约 45 分钟有效期）。旧数据可能因 e 语义变更被写成 now+4h 的未来过期时间，
     * ②可兜底识别已死链接并触发回源刷新。</p>
     */
    private static boolean isMediaUrlFresh(Track track) {
        String url = track.getMediaUrl();
        OffsetDateTime expiresAt = track.getUrlExpiresAt();
        if (url == null || url.isBlank()) return false;
        if (!isFreshEnough(expiresAt)) return false;
        return LanzouApiClient.resolveRealExpiry(url).isAfter(Instant.now().plus(URL_SAFETY_MARGIN));
    }

    /** 直链是否仍有足够的安全余量（低于 {@link #URL_SAFETY_MARGIN} 视为需要续期）。 */
    private static boolean isFreshEnough(OffsetDateTime expiresAt) {
        return expiresAt != null && expiresAt.isAfter(OffsetDateTime.now().plus(URL_SAFETY_MARGIN));
    }

    /**
     * 直链健康预检：用 Range 请求（bytes=0-0）探测直链是否真的可拉取，尽量贴近浏览器播放的
     * 真实可达性（不带会话 Cookie，避免「带 Cookie 才能下」的假阳性）。
     * <ul>
     *   <li>200/206 → 可播放（音频服务常返回 206 部分内容）</li>
     *   <li>403/404/410 等明确 4xx → 不可播（链接失效/被拒）</li>
     *   <li>5xx / 超时 / 网络异常 → 视为可播放（保守，避免把临时抖动误判为死链而反复回源）</li>
     * </ul>
     * 单次预检结果是「建议性」：即便误判为不可播，上层因不缓存负面结果，下个请求会重新校验自愈。
     */
    private static boolean checkPlayable(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", UA)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .get()
                    .build();
            try (okhttp3.Response resp = mediaHealthClient.newCall(req).execute()) {
                int code = resp.code();
                if (code == 200 || code == 206) return true;
                if (code >= 400 && code < 500) {
                    // 403 常见为 CDN 拒绝失效链接；404/410 为文件不存在/已取消。这些视为不可播。
                    // 429(限流)/401(鉴权) 语义模糊，保守按可播处理，避免误判死链。
                    if (code == 403 || code == 404 || code == 410) return false;
                    return true;
                }
                // 5xx / 其它 → 保守按可播处理
                return true;
            }
        } catch (Exception e) {
            // 网络/超时等异常：保守按可播处理，避免单次抖动触发反复回源
            return true;
        }
    }

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";

    private static final okhttp3.OkHttpClient mediaHealthClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private String downloadText(String url) {
        okhttp3.Request req = new okhttp3.Request.Builder().url(url).build();
        try (okhttp3.Response resp = sharedLyricsClient.newCall(req).execute()) {
            return resp.body() != null ? resp.body().string() : "";
        } catch (Exception e) {
            throw toLanzouBusinessException(e, "下载失败");
        }
    }

    private static final okhttp3.OkHttpClient sharedLyricsClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private static <T> PageResponse<T> paginate(List<T> all, int page, int pageSize) {
        int total = all.size(), from = Math.min((page - 1) * pageSize, total), to = Math.min(from + pageSize, total);
        return PageResponse.<T>builder().items(new ArrayList<>(all.subList(from, to)))
                .page(page).pageSize(pageSize).total((long) total).hasMore(to < total).build();
    }

    /** 从目录树解析音频格式（扩展名）；找不到返回空串，避免把库里的 format 写成 null。 */
    private String resolveFormat(String id) {
        SongFolder sf = indexSongFoldersById().get(id);
        return sf == null ? "" : sf.parseFolderName().format();
    }

    private static boolean isAudio(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1 && AUDIO_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }
    private static int normalize(Integer v, int fallback) { return v == null || v < 1 ? fallback : v; }

    /**
     * 将直链真实过期时间（Instant）转为 OffsetDateTime，并留出 30 秒安全裕量，
     * 避免刚好卡在过期边界时返回已失效直链。
     */
    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        if (instant == null) return OffsetDateTime.now().plusMinutes(45);
        return OffsetDateTime.ofInstant(instant.minusSeconds(30), ZoneOffset.UTC);
    }

    /**
     * 将蓝奏云异常转换为业务异常：
     * 只有真正的“会话失效/未登录”才返回 LANZOU_SESSION_EXPIRED，
     * 其他（分享链接失效、反爬、文件被取消分享等）返回 MEDIA_UNAVAILABLE 并记录真实原因，
     * 避免把所有蓝奏云错误都误导成“会话过期”。
     */
    private static BusinessException toLanzouBusinessException(Throwable e, String fallback) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof LanzouSessionException) {
                String msg = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
                boolean expired = msg.contains("session invalid")
                        || msg.contains("zt=9")
                        || msg.contains("login not")
                        || msg.contains("not login")
                        || msg.contains("extract uid/vei failed")
                        || msg.contains("missing cookie: phpdisk_info");
                if (expired) {
                    return new BusinessException(ErrorCode.LANZOU_SESSION_EXPIRED);
                }
                log.warn("蓝奏云非会话类错误: {}", cause.getMessage());
                return new BusinessException(ErrorCode.MEDIA_UNAVAILABLE,
                        "播放地址暂时不可用: " + cause.getMessage());
            }
            cause = cause.getCause();
        }
        log.warn("音乐直链获取失败: {}", e.getMessage(), e);
        return new BusinessException(ErrorCode.MEDIA_UNAVAILABLE, fallback + ": " + e.getMessage());
    }

    private static String requireTrackId(String trackId) {
        String s = trim(trackId);
        if (s.isEmpty()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "trackId 不能为空");
        return s;
    }
    private static boolean containsIgnoreCase(String source, String kwLower) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(kwLower);
    }

    record ParsedName(String name, String artist, String format, boolean isLyric) {}
    @Override
    public java.util.List<String> getAllTrackIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (SongFolder sf : loadSongFolders()) {
            ids.add(sf.audioFile().id());
        }
        return ids;
    }
}
