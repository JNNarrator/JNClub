package com.jnclub.bookmark.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 死链探测判定表测试（probeUrl 策略层 + probeHttp 机制层 + normalizeProbeTarget 归一化）。
 *
 * <p>契约：1=正常 / 2=失效 / 0=无法判断。</p>
 *
 * <p><b>2 意味着前端「一键清理」会 removeById 永久删除（不进回收站），
 * 因此只有确定性死亡信号才允许判 2。</b>本测试的首要目的是钉住「什么不该被判 2」。</p>
 *
 * <p>关键回归点：</p>
 * <ul>
 *   <li>HEAD 抛异常时**不得**叠加第二次 GET 超时——这是接口挂起 60s 的根因。</li>
 *   <li>DNS 解析失败 / 连接被拒 = 主机已消失 → 2（真死链），否则失效检测失去核心价值。</li>
 *   <li>403 / 429 / 401 / 451 / 408 / 5xx = 活着但暂时问不出来 → 0，否则会误删正常收藏。</li>
 *   <li>只有 GET 复核后的 404 / 410 判 2。</li>
 *   <li>非 http(s) 协议、回环地址、单标签主机一律不探测（0）。</li>
 * </ul>
 *
 * <p><b>为什么状态码用例测 probeHttp 而不是 probeUrl</b>：probeUrl 的主机护栏会拦掉
 * 127.0.0.1（服务端探测回环无意义），而本地桩只能绑 127.0.0.1。所以按层次分别测：
 * 机制层用真实 HTTP 桩，策略层用不需要网络的输入。</p>
 */
class BookmarkServiceProbeUrlTest {

    /** 本地桩绑定地址（回环；probeUrl 护栏会拦，故状态码用例走 probeHttp） */
    private static final String STUB_HOST = "127.0.0.1";

    private HttpServer server;
    private int port;
    private ExecutorService serverPool;
    private Method probeUrlMethod;
    private Method probeHttpMethod;
    private BookmarkService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(STUB_HOST, 0), 0);
        port = server.getAddress().getPort();
        serverPool = Executors.newCachedThreadPool();
        server.setExecutor(serverPool);
        server.start();

        service = new BookmarkService();
        probeUrlMethod = BookmarkService.class.getDeclaredMethod("probeUrl", String.class);
        probeUrlMethod.setAccessible(true);
        probeHttpMethod = BookmarkService.class.getDeclaredMethod("probeHttp", String.class);
        probeHttpMethod.setAccessible(true);
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

    /** 策略层：协议/主机护栏 + 归一化 + 探测（不应对外网发起请求） */
    private int probe(String url) throws Exception {
        return (int) probeUrlMethod.invoke(service, url);
    }

    /** 机制层：直接对已归一化的 URL 探测（绕过主机护栏，可用 127.0.0.1 桩） */
    private int probeRaw(String url) throws Exception {
        return (int) probeHttpMethod.invoke(service, url);
    }

    /** 注册桩：HEAD 与 GET 分别返回不同状态码，用于验证 GET 复核逻辑 */
    private void stub(String path, int headStatus, int getStatus) {
        server.createContext(path, exchange -> {
            boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
            exchange.sendResponseHeaders(isHead ? headStatus : getStatus, -1);
            exchange.close();
        });
    }

    /** 注册桩：HEAD/GET 同码 */
    private void stubSame(String path, int status) {
        stub(path, status, status);
    }

    private String url(String path) {
        return "http://" + STUB_HOST + ":" + port + path;
    }

    // ==================== 机制层：基础判定 ====================

    @Test
    @DisplayName("HEAD 200 → 1 正常")
    void head200_判定为正常() throws Exception {
        stubSame("/ok", 200);
        assertEquals(1, probeRaw(url("/ok")));
    }

    @Test
    @DisplayName("HEAD 204 → 1 正常（2xx 无内容也算活着）")
    void head204_判定为正常() throws Exception {
        stubSame("/no-content", 204);
        assertEquals(1, probeRaw(url("/no-content")));
    }

    @Test
    @DisplayName("HEAD 404 且 GET 404 → 2 失效（GET 复核后确认不存在）")
    void head404_get404_判定为失效() throws Exception {
        stubSame("/gone", 404);
        assertEquals(2, probeRaw(url("/gone")));
    }

    @Test
    @DisplayName("HEAD 410 且 GET 410 → 2 失效（资源永久移除）")
    void head410_判定为失效() throws Exception {
        stubSame("/removed", 410);
        assertEquals(2, probeRaw(url("/removed")));
    }

    @Test
    @DisplayName("HEAD 404 但 GET 200 → 1 正常（404 也走 GET 复核，避免误删）")
    void head404_get200_判定为正常() throws Exception {
        stub("/head-404-only", 404, 200);
        assertEquals(1, probeRaw(url("/head-404-only")));
    }

    @Test
    @DisplayName("HEAD 301 跟随到 200 → 1 正常")
    void head301跟随到200_判定为正常() throws Exception {
        stubSame("/target", 200);
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", url("/target"));
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        assertEquals(1, probeRaw(url("/moved")));
    }

    @Test
    @DisplayName("HEAD 302 跟随到 404 → 2 失效（跟随后的状态码才作数）")
    void head302跟随到404_判定为失效() throws Exception {
        stubSame("/missing", 404);
        server.createContext("/temp-moved", exchange -> {
            exchange.getResponseHeaders().add("Location", url("/missing"));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        assertEquals(2, probeRaw(url("/temp-moved")));
    }

    @Test
    @DisplayName("带 user:pass 的 URL → 1 正常（凭据不影响判定）")
    void 带凭据的URL_判定为正常() throws Exception {
        stubSame("/auth", 200);
        assertEquals(1, probeRaw("http://user:pass@" + STUB_HOST + ":" + port + "/auth"));
    }

    // ==================== 机制层：活着但问不出来 → 0（防误删） ====================

    /**
     * P1 数据丢失风险回归防线：这些状态码全部代表「站点活着」，
     * 一旦被判 2，前端「一键清理」会 removeById 永久删除（不进回收站）。
     */
    @Test
    @DisplayName("401 / 408 / 451 / 500 / 502 / 503 / 504 → 0（站点活着，禁止判死链）")
    void 非确定性4xx5xx_返回0() throws Exception {
        // 401 需登录、408 超时、451 法律屏蔽、5xx 维护与网关抖动
        int[] codes = {401, 408, 451, 500, 502, 503, 504};
        for (int code : codes) {
            String path = "/c" + code;
            stubSame(path, code);
            assertEquals(0, probeRaw(url(path)), "HTTP " + code + " 属「活着但问不出来」，必须为 0");
        }
    }

    @Test
    @DisplayName("HEAD 429 → 0（限流，绝不判死链）")
    void head429_返回0() throws Exception {
        stubSame("/rate-limited", 429);
        assertEquals(0, probeRaw(url("/rate-limited")));
    }

    // ==================== 机制层：GET 复核 ====================

    @Test
    @DisplayName("HEAD 405 但 GET 200 → 1 正常（服务端不支持 HEAD）")
    void head405_get200_判定为正常() throws Exception {
        stub("/no-head", 405, 200);
        assertEquals(1, probeRaw(url("/no-head")));
    }

    @Test
    @DisplayName("HEAD 501 但 GET 200 → 1 正常")
    void head501_get200_判定为正常() throws Exception {
        stub("/not-impl", 501, 200);
        assertEquals(1, probeRaw(url("/not-impl")));
    }

    @Test
    @DisplayName("HEAD 403 且 GET 403 → 0（反爬站点活着，不能判死链）")
    void head403_get403_返回0() throws Exception {
        stub("/forbidden", 403, 403);
        assertEquals(0, probeRaw(url("/forbidden")));
    }

    @Test
    @DisplayName("HEAD 403 但 GET 200 → 1 正常（回退纠正了误判）")
    void head403_get200_判定为正常() throws Exception {
        stub("/blocked", 403, 200);
        assertEquals(1, probeRaw(url("/blocked")));
    }

    @Test
    @DisplayName("HEAD 403 且 GET 429 → 0（回退后仍被限流）")
    void head403_get429_返回0() throws Exception {
        stub("/blocked-then-limited", 403, 429);
        assertEquals(0, probeRaw(url("/blocked-then-limited")));
    }

    @Test
    @DisplayName("HEAD 405 但 GET 404 → 2 失效（回退后确认页面不存在）")
    void head405_get404_判定为失效() throws Exception {
        stub("/no-head-gone", 405, 404);
        assertEquals(2, probeRaw(url("/no-head-gone")));
    }

    // ==================== 机制层：异常路径 ====================

    @Test
    @DisplayName("连接被拒绝（端口无人监听）→ 2 失效（主机已消失，真死链）")
    void 连接被拒绝_返回2() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        assertEquals(2, probeRaw("http://" + STUB_HOST + ":" + closedPort + "/anything"));
    }

    @Test
    @DisplayName("连接被拒只探测一次，耗时远小于单次超时（不叠加第二次 GET）")
    void 连接失败_不叠加二次超时() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        long start = System.currentTimeMillis();
        probeRaw("http://" + STUB_HOST + ":" + closedPort + "/anything");
        long cost = System.currentTimeMillis() - start;
        assertTrue(cost < 5_000, "单条探测耗时 " + cost + "ms，疑似叠加了第二次超时");
    }

    @Test
    @DisplayName("DNS 解析失败（域名已过期/被删）→ 2 失效（真死链）")
    void dns失败_返回2() throws Exception {
        // .invalid 是 RFC 2606 保留 TLD，保证不会解析成功
        assertEquals(2, probeRaw("http://this-host-should-not-resolve.invalid/"));
    }

    @Test
    @Timeout(20)
    @DisplayName("读超时 → 0 无法判断，且只等一个超时周期（不叠加 GET 的第二次超时）")
    void 读超时_返回0且不叠加二次超时() throws Exception {
        server.createContext("/hang", exchange -> {
            try {
                Thread.sleep(8_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception ignored) {
                // 客户端已断开，忽略
            } finally {
                exchange.close();
            }
        });
        long start = System.currentTimeMillis();
        int status = probeRaw(url("/hang"));
        long cost = System.currentTimeMillis() - start;
        assertEquals(0, status, "超时属于无法判断，不能判死链");
        // 单次超时 5s；若叠加 GET 复核会到 10s 以上
        assertTrue(cost < 7_500, "单条探测耗时 " + cost + "ms，疑似叠加了第二次超时");
    }

    // ==================== 策略层：协议 / 主机护栏（不发起网络请求） ====================

    @Test
    @DisplayName("非 http/https 协议 → 0，且不发起任何请求")
    void 非http协议_返回0() throws Exception {
        assertEquals(0, probe("mailto:a@b.com"));
        assertEquals(0, probe("tel:+8613800000000"));
        assertEquals(0, probe("javascript:alert(1)"));
        assertEquals(0, probe("file:///Users/x.html"));
        assertEquals(0, probe("obsidian://open?vault=notes"));
        assertEquals(0, probe("ftp://example.com/some/file"));
        assertEquals(0, probe("chrome-extension://abcdefg/popup.html"));
    }

    @Test
    @DisplayName("回环 / 未指定地址 → 0（服务端探测自己的回环判断不了用户侧）")
    void 回环地址_返回0() throws Exception {
        assertEquals(0, probe("localhost:3000"));
        assertEquals(0, probe("http://localhost:8080/x"));
        assertEquals(0, probe("http://127.0.0.1:3000/x"));
        assertEquals(0, probe("http://0.0.0.0:8080/x"));
        assertEquals(0, probe("http://[::1]:8080/x"));
        assertEquals(0, probe("http://api.localhost/x"));
    }

    @Test
    @DisplayName("单标签主机 / 垃圾串 → 0（不可能是公网域名，避免白跑 DNS 后误判死链）")
    void 单标签主机_返回0() throws Exception {
        assertEquals(0, probe("not-a-url"));
        assertEquals(0, probe("example"));
        assertEquals(0, probe("http://intranet/x"));
    }

    @Test
    @DisplayName("null / 空串 / 纯空白 → 0")
    void 空URL_返回0() throws Exception {
        assertEquals(0, probe(null));
        assertEquals(0, probe(""));
        assertEquals(0, probe("   "));
    }

    @Test
    @DisplayName("畸形 URL（缺 host / 非法 authority）→ 0，不抛异常")
    void 畸形URL_返回0() throws Exception {
        assertEquals(0, probe("http://"));
        assertEquals(0, probe("http://[::1/"));
    }

    // ==================== 归一化：纯函数断言 ====================

    @Test
    @DisplayName("裸域名 / 裸 host:port 补 http:// 前缀")
    void 归一化_补默认协议() {
        assertEquals("http://example.com", BookmarkService.normalizeProbeTarget("example.com"));
        assertEquals("http://example.com:8080/x", BookmarkService.normalizeProbeTarget("example.com:8080/x"));
        // 协议相对 URL 补 "http:" 而非 "http://"，否则会出现 http:////example.com
        assertEquals("http://example.com/x", BookmarkService.normalizeProbeTarget("//example.com/x"));
    }

    @Test
    @DisplayName("含 :// 的 query 不被误当成协议（旧实现会永久跳过探测）")
    void 归一化_query含双斜杠不被误判() {
        assertEquals("http://example.com/path?u=http://foo",
                BookmarkService.normalizeProbeTarget("example.com/path?u=http://foo"));
        assertEquals("https://example.com/path?next=http://bar",
                BookmarkService.normalizeProbeTarget("https://example.com/path?next=http://bar"));
    }

    @Test
    @DisplayName("已是 http/https 的原样返回")
    void 归一化_保留http协议() {
        assertEquals("http://example.com/x", BookmarkService.normalizeProbeTarget("http://example.com/x"));
        assertEquals("https://example.com/x", BookmarkService.normalizeProbeTarget("https://example.com/x"));
    }

    @Test
    @DisplayName("不可探测的输入归一化为 null")
    void 归一化_不可探测返回null() {
        assertNull(BookmarkService.normalizeProbeTarget("mailto:a@b.com"));
        assertNull(BookmarkService.normalizeProbeTarget("obsidian://open?vault=notes"));
        assertNull(BookmarkService.normalizeProbeTarget("localhost:3000"));
        assertNull(BookmarkService.normalizeProbeTarget("127.0.0.1:8080/x"));
        assertNull(BookmarkService.normalizeProbeTarget("not-a-url"));
        assertNull(BookmarkService.normalizeProbeTarget("http://"));
        assertNull(BookmarkService.normalizeProbeTarget("http://[::1/"));
    }
}
