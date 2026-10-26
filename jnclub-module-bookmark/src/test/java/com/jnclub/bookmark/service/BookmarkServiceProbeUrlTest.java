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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BookmarkService.probeUrl 返回码判定表测试。
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
 *   <li>只有 404 / 410 判 2；非 http(s) 协议一律不探测。</li>
 * </ul>
 *
 * <p>用 JDK 自带的 {@code com.sun.net.httpserver} 起本地桩服务，不引入任何新依赖、不访问外网。</p>
 */
class BookmarkServiceProbeUrlTest {

    private HttpServer server;
    private int port;
    private ExecutorService serverPool;
    private Method probeUrl;
    private BookmarkService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        serverPool = Executors.newCachedThreadPool();
        server.setExecutor(serverPool);
        server.start();

        service = new BookmarkService();
        probeUrl = BookmarkService.class.getDeclaredMethod("probeUrl", String.class);
        probeUrl.setAccessible(true);
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

    /** 调用被测私有方法 */
    private int probe(String url) throws Exception {
        return (int) probeUrl.invoke(service, url);
    }

    /** 注册一个桩路径：HEAD 与 GET 分别返回不同状态码，用于验证回退逻辑 */
    private void stub(String path, int headStatus, int getStatus) {
        server.createContext(path, exchange -> {
            boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
            exchange.sendResponseHeaders(isHead ? headStatus : getStatus, -1);
            exchange.close();
        });
    }

    /** 注册一个只按方法返回指定状态码的桩（HEAD/GET 同码） */
    private void stubSame(String path, int status) {
        stub(path, status, status);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    // ---------------- 基础判定 ----------------

    @Test
    @DisplayName("HEAD 200 → 1 正常")
    void head200_判定为正常() throws Exception {
        stubSame("/ok", 200);
        assertEquals(1, probe(url("/ok")));
    }

    @Test
    @DisplayName("HEAD 204 → 1 正常（2xx 无内容也算活着）")
    void head204_判定为正常() throws Exception {
        stubSame("/no-content", 204);
        assertEquals(1, probe(url("/no-content")));
    }

    @Test
    @DisplayName("带 user:pass 的 URL → 1 正常（凭据不影响判定）")
    void 带凭据的URL_判定为正常() throws Exception {
        stubSame("/auth", 200);
        assertEquals(1, probe("http://user:pass@127.0.0.1:" + port + "/auth"));
    }

    @Test
    @DisplayName("HEAD 404 → 2 失效（真死链）")
    void head404_判定为失效() throws Exception {
        stubSame("/gone", 404);
        assertEquals(2, probe(url("/gone")));
    }

    @Test
    @DisplayName("HEAD 500 → 0 无法判断（服务端错误常为瞬时，判死链会被永久删除）")
    void head500_返回0() throws Exception {
        stubSame("/boom", 500);
        assertEquals(0, probe(url("/boom")));
    }

    @Test
    @DisplayName("HEAD 410 → 2 失效（资源已永久移除，与 404 同为确定性死亡信号）")
    void head410_判定为失效() throws Exception {
        stubSame("/removed", 410);
        assertEquals(2, probe(url("/removed")));
    }

    @Test
    @DisplayName("HEAD 301 带 Location 跟随到 200 → 1 正常（跟随重定向生效）")
    void head301跟随到200_判定为正常() throws Exception {
        stubSame("/target", 200);
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", url("/target"));
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        assertEquals(1, probe(url("/moved")));
    }

    @Test
    @DisplayName("HEAD 302 带 Location 但目标 404 → 2 失效（跟随后的状态码才作数）")
    void head302跟随到404_判定为失效() throws Exception {
        stubSame("/missing", 404);
        server.createContext("/temp-moved", exchange -> {
            exchange.getResponseHeaders().add("Location", url("/missing"));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        assertEquals(2, probe(url("/temp-moved")));
    }

    // ---------------- 429 限流 / 4xx-5xx「活着但问不出来」：不能判死链（防误删） ----------------

    @Test
    @DisplayName("HEAD 429 → 0 无法判断（限流，绝不判死链，否则一键清理会误删）")
    void head429_返回0() throws Exception {
        stubSame("/rate-limited", 429);
        assertEquals(0, probe(url("/rate-limited")));
    }

    /**
     * 这一组是 P1 数据丢失风险的回归防线：这些状态码全部代表「站点活着」，
     * 一旦被判 2，前端「一键清理」会 removeById 永久删除（不进回收站）。
     */
    @Test
    @DisplayName("401 / 408 / 451 / 502 / 503 / 504 → 0 无法判断（站点活着，禁止判死链）")
    void 非确定性4xx5xx_返回0() throws Exception {
        // 401 需登录、408 请求超时、451 法律屏蔽、502/503/504 维护与网关抖动
        int[] codes = {401, 408, 451, 502, 503, 504};
        for (int code : codes) {
            String path = "/c" + code;
            stubSame(path, code);
            assertEquals(0, probe(url(path)), "HTTP " + code + " 属「活着但问不出来」，必须为 0");
        }
    }

    @Test
    @DisplayName("HEAD 403 且 GET 429 → 0 无法判断（回退后仍被限流）")
    void head403_get429_返回0() throws Exception {
        stub("/blocked-then-limited", 403, 429);
        assertEquals(0, probe(url("/blocked-then-limited")));
    }

    // ---------------- 403/405/501 回退 GET ----------------

    @Test
    @DisplayName("HEAD 405 但 GET 200 → 1 正常（服务端不支持 HEAD，回退生效）")
    void head405_get200_判定为正常() throws Exception {
        stub("/no-head", 405, 200);
        assertEquals(1, probe(url("/no-head")));
    }

    @Test
    @DisplayName("HEAD 501 但 GET 200 → 1 正常（回退生效）")
    void head501_get200_判定为正常() throws Exception {
        stub("/not-impl", 501, 200);
        assertEquals(1, probe(url("/not-impl")));
    }

    @Test
    @DisplayName("HEAD 403 且 GET 也 403 → 0 无法判断（反爬站点活着，不能判死链）")
    void head403_get403_返回0() throws Exception {
        stub("/forbidden", 403, 403);
        assertEquals(0, probe(url("/forbidden")));
    }

    @Test
    @DisplayName("HEAD 403 但 GET 200 → 1 正常（403 是反爬而非死链，回退纠正了误判）")
    void head403_get200_判定为正常() throws Exception {
        stub("/blocked", 403, 200);
        assertEquals(1, probe(url("/blocked")));
    }

    @Test
    @DisplayName("HEAD 405 但 GET 404 → 2 失效（回退后确认页面不存在）")
    void head405_get404_判定为失效() throws Exception {
        stub("/no-head-gone", 405, 404);
        assertEquals(2, probe(url("/no-head-gone")));
    }

    // ---------------- 异常路径：主机已消失 → 2 ----------------

    @Test
    @DisplayName("连接被拒绝（端口无人监听）→ 2 失效（主机已消失，真死链）")
    void 连接被拒绝_返回2() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        // 端口已释放，连接必然被拒绝
        assertEquals(2, probe("http://127.0.0.1:" + closedPort + "/anything"));
    }

    @Test
    @DisplayName("连接被拒只探测一次，耗时不超过单次超时（不叠加第二次 GET）")
    void 连接失败_不叠加二次超时() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        long start = System.currentTimeMillis();
        probe("http://127.0.0.1:" + closedPort + "/anything");
        long cost = System.currentTimeMillis() - start;
        // 连接拒绝是快速失败，远小于 5s 单次超时；若出现 2×5s 叠加说明回退逻辑退化
        assertTrue(cost < 5_000, "单条探测耗时 " + cost + "ms，疑似叠加了第二次超时");
    }

    @Test
    @DisplayName("DNS 解析失败（域名已过期/被删）→ 2 失效（真死链）")
    void dns失败_返回2() throws Exception {
        assertEquals(2, probe("http://this-host-should-not-resolve.invalid/"));
    }

    // ---------------- 异常路径：无法判断 → 0 ----------------

    @Test
    @Timeout(15)
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
        int status = probe(url("/hang"));
        long cost = System.currentTimeMillis() - start;
        assertEquals(0, status, "超时属于无法判断，不能判死链");
        assertTrue(cost < 9_000, "单条探测耗时 " + cost + "ms，疑似叠加了第二次超时");
    }

    // ---------------- 入参边界 ----------------

    @Test
    @DisplayName("null / 空串 / 纯空白 URL → 0 无法判断，且不发起请求")
    void 空URL_返回0() throws Exception {
        assertEquals(0, probe(null));
        assertEquals(0, probe(""));
        assertEquals(0, probe("   "));
    }

    @Test
    @DisplayName("非 http/https 协议 → 0 无法判断（不得判死链，否则一键清理会误删）")
    void 非http协议_返回0() throws Exception {
        // Hutool 会把协议名当主机名解析，必然 UnknownHostException；
        // 若不拦截，obsidian:// / ftp:// 这类有效收藏会被判失效并被真删
        assertEquals(0, probe("obsidian://open?vault=notes"));
        assertEquals(0, probe("ftp://127.0.0.1/some/file"));
        assertEquals(0, probe("chrome-extension://abcdefg/popup.html"));
        assertEquals(0, probe("file:///Users/x.html"));
        // scheme: 形式（无 //）必须同样拦住——实测曾漏网被判 2 并会被真删
        assertEquals(0, probe("mailto:a@b.com"));
        assertEquals(0, probe("tel:+8613800000000"));
    }

    // ---------------- URL 归一化：scheme 正则 vs indexOf("://") ----------------

    @Test
    @DisplayName("裸 host:port（无协议）→ 按 http 正常探测，不把 host 当协议")
    void 裸host端口_能正常探测() throws Exception {
        stubSame("/ok", 200);
        // localhost:3000 这类曾被解析成 scheme="localhost" → 误判死链
        assertEquals(1, probe("127.0.0.1:" + port + "/ok"));
    }

    @Test
    @DisplayName("裸域名带含 :// 的 query → 仍能正常探测（旧实现被永久跳过）")
    void 裸域名query含双斜杠_能正常探测() throws Exception {
        stubSame("/ok2", 200);
        // 旧实现用 indexOf("://") 会命中 query 里的 ://，把 scheme 误取成
        // "127.0.0.1:port/ok2?u=http" → 非 http → 永久返回 0，永远无法判定
        assertEquals(1, probe("127.0.0.1:" + port + "/ok2?u=http://foo.example"));
    }

    @Test
    @DisplayName("协议相对 URL（//host/path）→ 补 http: 前缀后可探测")
    void 协议相对URL_能正常探测() throws Exception {
        stubSame("/ok3", 200);
        assertEquals(1, probe("//127.0.0.1:" + port + "/ok3"));
    }

    @Test
    @DisplayName("畸形 URL（缺 host / 非法 authority）→ 0 无法判断，不抛异常")
    void 畸形URL_返回0() throws Exception {
        assertEquals(0, probe("http://"));
        assertEquals(0, probe("http://[::1/"));
    }
}
