package com.jnclub.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CorsConfig 单元测试 — 白名单行为与「通配来源 + 允许凭据」回归防线。
 * <p>
 * 一旦有人把来源改回 {@code "*"}、或把 {@code allowCredentials} 打开，本测试必须失败。
 */
class CorsConfigTest {

    private static final String ALLOWED = "http://localhost:5173";
    private static final String EVIL = "https://evil.example";

    /** 发起一次带 Origin 的预检请求，返回响应 */
    private MockHttpServletResponse preflight(String origin, CorsFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/bookmarks");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "jn-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowedOriginPassesPreflight() throws Exception {
        CorsFilter filter = new CorsConfig(ALLOWED, "").corsFilter();
        assertEquals(ALLOWED, preflight(ALLOWED, filter).getHeader("Access-Control-Allow-Origin"),
                "白名单来源应获得跨域许可");
    }

    @Test
    void unlistedOriginIsRejected() throws Exception {
        CorsFilter filter = new CorsConfig(ALLOWED, "").corsFilter();
        MockHttpServletResponse response = preflight(EVIL, filter);
        assertNull(response.getHeader("Access-Control-Allow-Origin"), "非白名单来源不得获得跨域许可");
        assertEquals(403, response.getStatus(), "非白名单预检应被拒绝");
    }

    @Test
    void credentialsAreNotAllowed() throws Exception {
        CorsFilter filter = new CorsConfig(ALLOWED, "").corsFilter();
        assertNotEquals("true", preflight(ALLOWED, filter).getHeader("Access-Control-Allow-Credentials"),
                "不应声明允许携带凭据");
    }

    @Test
    void wildcardIsNeverAccepted() throws Exception {
        CorsFilter filter = new CorsConfig(CorsConfig.DEFAULT_ALLOWED_ORIGINS, "").corsFilter();
        assertNull(preflight("*", filter).getHeader("Access-Control-Allow-Origin"), "不得回显通配来源");
        assertNull(preflight("null", filter).getHeader("Access-Control-Allow-Origin"), "不得接受 null 来源");
        assertEquals("http://localhost:5173", preflight("http://localhost:5173", filter)
                .getHeader("Access-Control-Allow-Origin"), "默认白名单应含本地 Vite dev");
    }

    /** frontendUrl 应被自动折算成 origin 加入白名单（带路径/无尾斜杠也要能解析） */
    @Test
    void frontendUrlIsAddedAsOrigin() throws Exception {
        CorsFilter filter = new CorsConfig(ALLOWED, "https://jnclub.example.vip/some/path").corsFilter();
        assertEquals("https://jnclub.example.vip",
                preflight("https://jnclub.example.vip", filter).getHeader("Access-Control-Allow-Origin"),
                "frontendUrl 应自动加入白名单（去掉路径部分）");
        assertNull(preflight("https://jnclub.example.vip.evil.com", filter)
                .getHeader("Access-Control-Allow-Origin"), "不得因前缀匹配而放行伪造来源");
    }
}
