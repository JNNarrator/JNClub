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
            response.sendRedirect(serverUrl + "/auth"
                + "?client=" + URLEncoder.encode("app-jnclub", StandardCharsets.UTF_8)
                + "&redirect=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8));
            return;
        }

        try {
            HttpResponse res = HttpRequest.post(serverUrl + "/api/ticket/apply")
                .form("ticket", ticket)
                .execute();

            String rawBody = res.body();
            log.info("SSO ticket 交换返回: status={}, body={}", res.getStatus(), rawBody);

            JSONObject result = JSONUtil.parseObj(rawBody);
            if (result.getInt("code") == 200) {
                JSONObject data = result.getJSONObject("data");
                if (data == null) {
                    log.error("SSO 返回 data 为空, body={}", rawBody);
                    response.sendRedirect(frontendUrl + "?error=sso_error");
                    return;
                }

                // userId 是 Long 类型，不能直接用 getStr，必须用 get + toString
                Object uidObj = data.get("userId");
                String userId = uidObj != null ? uidObj.toString() : null;

                // SSO 返回的字段名是 tokenValue（Sa-Token 标准字段）
                String ssoToken = data.getStr("tokenValue");
                if (ssoToken == null || ssoToken.isBlank()) {
                    // 兼容可能的 token 字段名
                    ssoToken = data.getStr("token");
                }

                if (userId == null || userId.isBlank()) {
                    log.error("SSO 返回未找到 userId, data keys={}", data.keySet());
                    response.sendRedirect(frontendUrl + "?error=sso_error");
                    return;
                }

                userInfoCache.put(userId, data);
                ssoTokenCache.put(userId, ssoToken);

                StpUtil.login(userId);
                String jnclubToken = StpUtil.getTokenValue();

                log.info("JNClub 登录成功, userId={}, ssoToken={}, jnclubToken={}",
                        userId, ssoToken, jnclubToken);

                String redirectUrl = frontendUrl;
                redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "token=" + jnclubToken;
                response.sendRedirect(redirectUrl);
            } else {
                log.error("SSO ticket 验证失败: code={}, msg={}",
                        result.getInt("code"), result.getStr("msg"));
                response.sendRedirect(frontendUrl + "?error=sso_failed");
            }
        } catch (Exception e) {
            log.error("SSO 登录异常", e);
            response.sendRedirect(frontendUrl + "?error=sso_error");
        }
    }

    @GetMapping("/logout")
    public void logout(HttpServletResponse response) throws IOException {
        String ssoToken = null;

        if (StpUtil.isLogin()) {
            try {
                String userId = StpUtil.getLoginIdAsString();
                ssoToken = ssoTokenCache.get(userId);
                userInfoCache.remove(userId);
                ssoTokenCache.remove(userId);
                StpUtil.logout();
                log.info("JNClub 本地退出成功, userId={}", userId);
            } catch (Exception e) {
                log.warn("JNClub 退出异常（忽略）", e);
            }
        }

        if (ssoToken != null && !ssoToken.isBlank()) {
            try {
                HttpResponse res = HttpRequest.post(serverUrl + "/api/user/logout")
                    .header("jn-token", ssoToken)
                    .execute();
                log.info("SSO 服务器退出响应: {}", res.body());
            } catch (Exception e) {
                log.error("调用 SSO 退出 API 失败", e);
            }
        }

        String loginUrl = serverUrl + "/login";
        log.info("重定向到 SSO 登录页: {}", loginUrl);
        response.sendRedirect(loginUrl);
    }
}
