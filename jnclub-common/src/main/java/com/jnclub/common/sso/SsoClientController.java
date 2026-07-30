package com.jnclub.common.sso;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/sso")
public class SsoClientController {

    @Value("${sa-token.sso.server-url}")
    private String serverUrl;

    @Value("${sa-token.sso.client-url}")
    private String clientUrl;

    @Value("${jnclub.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final Map<String, JSONObject> userInfoCache = new ConcurrentHashMap<>();
    private static final Map<String, String> ssoTokenCache = new ConcurrentHashMap<>();

    public static Map<String, JSONObject> getUserInfoCache() {
        return userInfoCache;
    }

    public static Map<String, String> getSsoTokenCache() {
        return ssoTokenCache;
    }

    public static String getSsoToken(String userId) {
        return ssoTokenCache.get(userId);
    }

    @GetMapping("/login")
    public void ssoLogin(String ticket, HttpServletResponse response) throws IOException {
        if (ticket == null || ticket.isBlank()) {
            String callbackUrl = clientUrl + "/sso/login";
            response.sendRedirect(serverUrl + "/sso/auth"
                + "?client=" + URLEncoder.encode("app-jnclub", StandardCharsets.UTF_8)
                + "&redirect=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8));
            return;
        }

        try {
            HttpResponse res = HttpRequest.post(serverUrl + "/sso/api/ticket/apply")
                .form("ticket", ticket)
                .execute();

            JSONObject result = JSONUtil.parseObj(res.body());
            if (result.getInt("code") == 200) {
                JSONObject data = result.getJSONObject("data");
                String userId = data.getStr("userId");
                String ssoToken = data.getStr("tokenValue");

                userInfoCache.put(userId, data);
                ssoTokenCache.put(userId, ssoToken);

                StpUtil.login(userId);
                String jnclubToken = StpUtil.getTokenValue();

                log.info("JNClub 登录成功, userId={}, ssoToken={}, jnclubToken={}", userId, ssoToken, jnclubToken);

                String redirectUrl = frontendUrl;
                redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "token=" + jnclubToken;
                response.sendRedirect(redirectUrl);
            } else {
                log.error("SSO ticket 验证失败: {}", result.getStr("msg"));
                response.sendRedirect(frontendUrl + "?error=sso_failed");
            }
        } catch (Exception e) {
            log.error("SSO 登录异常", e);
            response.sendRedirect(frontendUrl + "?error=sso_error");
        }
    }

    /**
     * 退出登录 - 主动调用 SSO 服务器退出 API，确保 SSO session 被清除
     */
    @GetMapping("/logout")
    public void logout(HttpServletResponse response) throws IOException {
        String ssoToken = null;
        
        // 获取当前用户的 SSO token
        if (StpUtil.isLogin()) {
            try {
                String userId = StpUtil.getLoginIdAsString();
                ssoToken = ssoTokenCache.get(userId);
                
                // 清除缓存
                userInfoCache.remove(userId);
                ssoTokenCache.remove(userId);
                
                // 清除 JNClub session
                StpUtil.logout();
                log.info("JNClub 本地退出成功, userId={}", userId);
            } catch (Exception e) {
                log.warn("JNClub 退出异常（忽略）", e);
            }
        }

        // 主动调用 SSO 服务器退出 API
        if (ssoToken != null && !ssoToken.isBlank()) {
            try {
                HttpResponse res = HttpRequest.post(serverUrl + "/sso/api/user/logout")
                    .header("jn-token", ssoToken)
                    .execute();
                log.info("SSO 服务器退出响应: {}", res.body());
            } catch (Exception e) {
                log.error("调用 SSO 退出 API 失败", e);
            }
        }

        // 跳回 SSO 登录页，让用户重新登录
        String loginUrl = serverUrl + "/sso/login";
        log.info("重定向到 SSO 登录页: {}", loginUrl);
        response.sendRedirect(loginUrl);
    }
}
