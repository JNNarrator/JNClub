package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jnclub.bookmark.entity.Bookmark;
import com.jnclub.common.cache.CacheService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@code BookmarkService.probeAll} / {@code checkDeadLinks} 层测试。
 *
 * <p>这是「check-dead 接口挂起 60s」事故的真正现场：串行探测、线程池泄漏、
 * 超时取消后统计口径失真、批量写库失败降级——{@code probeUrl} 层测不到这些。</p>
 *
 * <p><b>为什么要注入探测函数</b>：{@code probeUrl} 的策略层对回环地址（{@code 127.0.0.1}）
 * 一律返回「无法判断」，而本地桩只能跑在回环地址上。因此本测试分两层：</p>
 * <ul>
 *   <li><b>编排层</b>：调用 {@code probeAll(list, prober)}，注入假探测函数，
 *       只验证并发、id 映射、单条异常隔离、线程回收——与被探测目标无关。</li>
 *   <li><b>真实 HTTP 层</b>：反射取私有的 {@code probeHttp(target)}（已跳过策略层护栏），
 *       用本地桩验证 200/404 在 {@code probeAll} 全链路上的映射。</li>
 * </ul>
 */
class BookmarkServiceProbeAllTest {

    /** 与 BookmarkService.PROBE_THREADS 对齐：8 条任务应能全部并发 */
    private static final int PARALLEL_TASKS = 8;

    private HttpServer server;
    private int port;
    private ExecutorService serverPool;
    private BookmarkService service;
    /** 私有的机制层探测方法，已跳过协议/主机护栏 */
    private Method probeHttp;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        serverPool = Executors.newCachedThreadPool();
        server.setExecutor(serverPool);
        server.start();

        service = new BookmarkService();
        probeHttp = BookmarkService.class.getDeclaredMethod("probeHttp", String.class);
        probeHttp.setAccessible(true);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverPool != null) {
            serverPool.shutdownNow();
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private static Bookmark bookmark(long id, String url) {
        Bookmark b = new Bookmark();
        b.setId(id);
        b.setUrl(url);
        b.setTitle("t" + id);
        return b;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void stubSame(String path, int status) {
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    /** 走真实 HTTP 机制层的探测函数（绕过策略层护栏，允许回环地址） */
    private int probeHttpVia(String target) {
        try {
            return (int) probeHttp.invoke(service, target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long countProbeThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(t -> t.getName().startsWith("dead-link-probe"))
                .count();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================
    // 编排层：结构与覆盖（注入假探测函数）
    // ============================================================

    @Test
    @DisplayName("空收藏列表 → 返回空映射，不创建探测任务")
    void 空列表_返回空映射() {
        assertTrue(service.probeAll(List.of(), u -> 1).isEmpty());
    }

    @Test
    @DisplayName("每个 id 都有判定结果，且不含输入之外的 id")
    void 每个id都有判定结果() {
        List<Bookmark> bookmarks = List.of(
                bookmark(1L, "https://a.example/1"),
                bookmark(2L, "https://b.example/2"),
                bookmark(3L, "https://c.example/3"));

        Map<Long, Integer> result = service.probeAll(bookmarks, u -> u.contains("b.example") ? 2 : 1);

        assertEquals(3, result.size(), "每个收藏都必须有判定结果，否则统计口径会漏条");
        assertEquals(1, result.get(1L));
        assertEquals(2, result.get(2L));
        assertEquals(1, result.get(3L));
        assertFalse(result.containsKey(999L), "不得出现输入之外的 id");
    }

    @Test
    @DisplayName("探测函数收到的就是收藏的原始 url，未被改写")
    void 探测函数收到原始url() {
        List<String> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Bookmark> bookmarks = List.of(
                bookmark(1L, "https://a.example/x?q=1"),
                bookmark(2L, "https://b.example/y"));

        service.probeAll(bookmarks, u -> {
            seen.add(u);
            return 1;
        });

        assertEquals(2, seen.size());
        assertTrue(seen.contains("https://a.example/x?q=1"));
        assertTrue(seen.contains("https://b.example/y"));
    }

    @Test
    @DisplayName("单条探测抛异常只影响该条，不影响其它条（接口不 500 的前提）")
    void 单条异常不影响其它条() {
        List<Bookmark> bookmarks = List.of(
                bookmark(1L, "boom"),
                bookmark(2L, "ok"),
                bookmark(3L, "boom2"),
                bookmark(4L, "ok2"));

        Map<Long, Integer> result = service.probeAll(bookmarks, u -> {
            if (u.startsWith("boom")) {
                throw new IllegalStateException("模拟探测内部异常: " + u);
            }
            return 1;
        });

        assertEquals(4, result.size(), "抛异常的那条也要有结果（降级为 0），否则统计会漏条");
        assertEquals(0, result.get(1L), "异常条目降级为无法判断");
        assertEquals(1, result.get(2L), "异常条目不得拖垮正常条目");
        assertEquals(0, result.get(3L));
        assertEquals(1, result.get(4L));
    }

    @Test
    @DisplayName("null id 的收藏不会污染映射，也不会抛异常")
    void null_id不污染映射() {
        Bookmark noId = new Bookmark();
        noId.setUrl("https://a.example");
        List<Bookmark> bookmarks = List.of(noId, bookmark(2L, "https://b.example"));

        Map<Long, Integer> result = service.probeAll(bookmarks, u -> 1);

        assertFalse(result.containsKey(null), "null id 不得作为 key 写入");
        assertEquals(1, result.get(2L));
    }

    // ============================================================
    // 编排层：并行度与线程回收（60s → 秒级 的核心证据）
    // ============================================================

    @Test
    @Timeout(30)
    @DisplayName("并行探测：8 条各睡 800ms，总耗时应接近单条而非 8 倍（串行会 6.4s）")
    void 并行探测_总耗时接近单条而非串行() {
        long delayMs = 800L;
        List<Bookmark> bookmarks = new ArrayList<>();
        for (int i = 0; i < PARALLEL_TASKS; i++) {
            bookmarks.add(bookmark(i + 1, "https://h" + i + ".example/"));
        }

        long start = System.currentTimeMillis();
        Map<Long, Integer> result = service.probeAll(bookmarks, u -> {
            sleepQuietly(delayMs);
            return 1;
        });
        long cost = System.currentTimeMillis() - start;

        assertEquals(PARALLEL_TASKS, result.size());
        result.values().forEach(v -> assertEquals(1, v));

        long serialCost = delayMs * PARALLEL_TASKS;   // 6400ms
        // 阈值取串行耗时的 60%，既证明并发，又给慢 CI 留足余量
        long threshold = (long) (serialCost * 0.6);
        assertTrue(cost < threshold,
                "8 条 × " + delayMs + "ms 若串行需 " + serialCost + "ms，实测 " + cost
                        + "ms（阈值 " + threshold + "ms）——并发度疑似退化");
    }

    @Test
    @Timeout(30)
    @DisplayName("探测结束后不留探测线程（线程池在 finally 中 shutdown）")
    void 探测完成后无线程残留() throws Exception {
        long baseline = countProbeThreads();
        List<Bookmark> bookmarks = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            bookmarks.add(bookmark(i, "https://h" + i + ".example/"));
        }

        service.probeAll(bookmarks, u -> 1);

        // shutdownNow 后线程异步退出，最多等 5s
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline && countProbeThreads() > baseline) {
            Thread.sleep(50L);
        }
        assertEquals(baseline, countProbeThreads(),
                "探测结束后仍有 dead-link-probe 线程存活，线程池未正确回收");
    }

    // ============================================================
    // 真实 HTTP 层：经 probeAll 全链路
    // ============================================================

    @Test
    @Timeout(30)
    @DisplayName("真实 HTTP：200 → 1，404 → 2，经 probeAll 全链路映射正确")
    void 真实HTTP_状态码映射正确() {
        stubSame("/ok1", 200);
        stubSame("/ok2", 200);
        stubSame("/gone", 404);

        List<Bookmark> bookmarks = List.of(
                bookmark(1L, url("/ok1")),
                bookmark(2L, url("/ok2")),
                bookmark(3L, url("/gone")));

        Map<Long, Integer> result = service.probeAll(bookmarks, this::probeHttpVia);

        assertEquals(3, result.size());
        assertEquals(1, result.get(1L));
        assertEquals(1, result.get(2L));
        assertEquals(2, result.get(3L), "404 经 GET 复核仍不存在 → 真死链");
    }

    @Test
    @Timeout(30)
    @DisplayName("真实 HTTP：连接被拒 → 2（主机已消失），不影响同批其它条")
    void 真实HTTP_连接被拒判死链() throws Exception {
        stubSame("/ok", 200);
        int closedPort;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }

        List<Bookmark> bookmarks = List.of(
                bookmark(1L, url("/ok")),
                bookmark(2L, "http://127.0.0.1:" + closedPort + "/dead"));

        Map<Long, Integer> result = service.probeAll(bookmarks, this::probeHttpVia);

        assertEquals(1, result.get(1L));
        assertEquals(2, result.get(2L), "连接被拒 = 主机已消失 = 真死链");
    }

    // ============================================================
    // checkDeadLinks：统计口径、响应契约、批量写库（spy + 静态 mock）
    // ============================================================

    private BookmarkService spyService(List<Bookmark> listed, Map<Long, Integer> statusById) {
        BookmarkService svc = spy(new BookmarkService());
        ReflectionTestUtils.setField(svc, "cacheService", mock(CacheService.class));
        doReturn(listed).when(svc).list(org.mockito.ArgumentMatchers.<Wrapper<Bookmark>>any());
        doReturn(statusById).when(svc).probeAll(anyList());
        doReturn(true).when(svc).updateBatchById(anyCollection());
        return svc;
    }

    private Map<String, Object> callCheckDeadLinks(BookmarkService svc) {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            return svc.checkDeadLinks();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("ok + dead + error 恒等于 total（含「无法判断」与「被取消」等混合结果）")
    void 统计口径_三类之和恒等于总数() {
        List<Bookmark> listed = List.of(
                bookmark(1L, "https://a.example/1"),
                bookmark(2L, "https://a.example/2"),
                bookmark(3L, "https://a.example/3"),
                bookmark(4L, "https://a.example/4"),
                bookmark(5L, "https://a.example/5"),
                bookmark(6L, "https://a.example/6"),
                bookmark(7L, "https://a.example/7"));

        // 模拟 probeAll 的真实产物：3 条正常、2 条失效、1 条无法判断、1 条缺席（超时被取消）
        Map<Long, Integer> statusById = new LinkedHashMap<>();
        statusById.put(1L, 1);
        statusById.put(2L, 1);
        statusById.put(3L, 1);
        statusById.put(4L, 2);
        statusById.put(5L, 2);
        statusById.put(6L, 0);
        // 7L 缺席：invokeAll 超时取消，probeAll 不会写入

        Map<String, Object> result = callCheckDeadLinks(spyService(listed, statusById));

        int total = (int) result.get("total");
        int ok = (int) result.get("ok");
        int dead = (int) result.get("dead");
        int error = (int) result.get("error");

        assertEquals(7, total);
        assertEquals(3, ok);
        assertEquals(2, dead);
        assertEquals(2, error, "显式 0 与「缺席」都必须计入 error");
        assertEquals(total, ok + dead + error,
                "三类之和必须恒等于总数，否则前端统计条会误导用户");
    }

    @Test
    @Timeout(30)
    @DisplayName("响应契约：total/ok/dead/error/deadList，deadList 项含 id/title/url")
    void 响应契约_字段与前端对齐() {
        List<Bookmark> listed = List.of(bookmark(42L, "https://dead.example/x"));

        Map<Long, Integer> statusById = new LinkedHashMap<>();
        statusById.put(42L, 2);

        Map<String, Object> result = callCheckDeadLinks(spyService(listed, statusById));

        // DeadLinkModal.vue 依赖这些 key
        assertTrue(result.keySet().containsAll(List.of("total", "ok", "dead", "error", "deadList")),
                "响应 key 变更会直接打断前端 DeadLinkModal");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deadList = (List<Map<String, Object>>) result.get("deadList");
        assertEquals(1, deadList.size());
        Map<String, Object> item = deadList.get(0);
        assertEquals(42L, item.get("id"));
        assertNotNull(item.get("title"));
        assertEquals("https://dead.example/x", item.get("url"));
    }

    @Test
    @Timeout(30)
    @DisplayName("空收藏 → 直接返回零值，不探测、不写库")
    void 空收藏_返回零值且不写库() {
        BookmarkService svc = spyService(List.of(), Map.of());
        Map<String, Object> result = callCheckDeadLinks(svc);

        assertEquals(0, result.get("total"));
        assertEquals(0, result.get("ok"));
        assertEquals(0, result.get("dead"));
        assertEquals(0, result.get("error"));
        assertTrue(((List<?>) result.get("deadList")).isEmpty());
        verify(svc, org.mockito.Mockito.never()).updateBatchById(anyCollection());
    }

    @Test
    @Timeout(30)
    @DisplayName("批量写库的是只含 id/checkStatus/checkedAt 的 patch，不得携带整行陈旧字段")
    @SuppressWarnings("unchecked")
    void 批量写库_只写三列不覆盖整行() {
        Bookmark b1 = bookmark(1L, "https://a.example/1");
        b1.setTitle("原标题");
        b1.setIcon("http://icon");
        b1.setSortOrder(7);
        b1.setDirectoryId(99L);
        b1.setUserId("u1");
        Bookmark b2 = bookmark(2L, "https://a.example/2");
        b2.setTitle("也会失效");

        Map<Long, Integer> statusById = new LinkedHashMap<>();
        statusById.put(1L, 1);
        statusById.put(2L, 2);

        BookmarkService svc = spyService(List.of(b1, b2), statusById);
        callCheckDeadLinks(svc);

        ArgumentCaptor<Collection<Bookmark>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(svc).updateBatchById(captor.capture());

        Collection<Bookmark> patches = captor.getValue();
        assertEquals(2, patches.size());
        for (Bookmark patch : patches) {
            assertNotNull(patch.getId(), "patch 必须带主键");
            assertNotNull(patch.getCheckStatus(), "patch 必须带 checkStatus（0 也要写入）");
            assertNotNull(patch.getCheckedAt(), "patch 必须带 checkedAt");
            // 以下为「不得出现」的字段：一旦被带上，MyBatis-Plus 会整列回写，
            // 覆盖用户在探测窗口内的并发修改（改名 / 移动目录 / 拖动排序）
            assertNull(patch.getTitle(), "patch 不得携带 title，否则会覆盖并发修改");
            assertNull(patch.getUrl(), "patch 不得携带 url");
            assertNull(patch.getIcon(), "patch 不得携带 icon");
            assertNull(patch.getDirectoryId(), "patch 不得携带 directoryId");
            assertNull(patch.getSortOrder(), "patch 不得携带 sortOrder");
            assertNull(patch.getUserId(), "patch 不得携带 userId");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("checkStatus=0（无法判断）也必须落库，不能被当成「无需更新」跳过")
    @SuppressWarnings("unchecked")
    void 无法判断也要落库() {
        Map<Long, Integer> statusById = new LinkedHashMap<>();
        statusById.put(1L, 0);

        BookmarkService svc = spyService(List.of(bookmark(1L, "https://a.example/1")), statusById);
        callCheckDeadLinks(svc);

        ArgumentCaptor<Collection<Bookmark>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(svc).updateBatchById(captor.capture());
        Bookmark patch = captor.getValue().iterator().next();
        assertEquals(0, patch.getCheckStatus(), "0 是合法状态（未检测/无法判断），必须写入");
    }
}
