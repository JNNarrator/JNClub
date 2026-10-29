package com.jnclub.common.sso;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 桌面端回调地址的校验。
 *
 * <p>这个参数会把登录 token 送到它指向的地方，所以它是开放重定向的
 * 边界。放行一条不该放行的地址就等于交出别人的会话。
 */
class SsoClientControllerLoopbackTest {

    @Test
    void acceptsLoopbackAddressesWithAPortAndAPath() {
        assertTrue(SsoClientController.isLoopbackCallback("http://127.0.0.1:54321/callback"));
        assertTrue(SsoClientController.isLoopbackCallback("http://localhost:8080/callback"));
        assertTrue(SsoClientController.isLoopbackCallback("http://[::1]:1234/callback"));
        assertTrue(SsoClientController.isLoopbackCallback("HTTP://127.0.0.1:54321/callback"));
    }

    @Test
    void rejectsHostsThatOnlyLookLikeLoopback() {
        // "1.evil.com" 会被当成端口的一部分：没有数字校验就会放行。
        assertFalse(SsoClientController.isLoopbackCallback("http://127.0.0.1:1.evil.com/callback"));
        assertFalse(SsoClientController.isLoopbackCallback("http://localhost:8080.evil.com/callback"));
        assertFalse(SsoClientController.isLoopbackCallback("http://127.0.0.1.evil.com/callback"));
    }

    @Test
    void rejectsEverythingOffTheLoopbackInterface() {
        assertFalse(SsoClientController.isLoopbackCallback("https://evil.com/callback"));
        assertFalse(SsoClientController.isLoopbackCallback("http://192.168.1.5:8080/callback"));
        assertFalse(SsoClientController.isLoopbackCallback("http://127.0.0.1/"));
    }

    @Test
    void rejectsAddressesWithoutAPortOrAPath() {
        assertFalse(SsoClientController.isLoopbackCallback("http://127.0.0.1/callback"));
        assertFalse(SsoClientController.isLoopbackCallback("http://127.0.0.1:54321"));
        assertFalse(SsoClientController.isLoopbackCallback("http://127.0.0.1:"));
    }

    @Test
    void rejectsEmptyAndNonHttpValues() {
        assertFalse(SsoClientController.isLoopbackCallback(null));
        assertFalse(SsoClientController.isLoopbackCallback(""));
        assertFalse(SsoClientController.isLoopbackCallback("   "));
        assertFalse(SsoClientController.isLoopbackCallback("javascript:alert(1)"));
        assertFalse(SsoClientController.isLoopbackCallback("//127.0.0.1:54321/callback"));
    }
}
