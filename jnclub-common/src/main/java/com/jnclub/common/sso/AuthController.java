package com.jnclub.common.sso;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import com.jnclub.common.model.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${sa-token.sso.server-url}")
    private String serverUrl;

    @Value("${jnclub.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/userinfo")
    public R<Map<String, Object>> getUserinfo() {
        try {
            if (!StpUtil.isLogin()) {
                return R.fail(401, "未登录");
            }
            String userId = StpUtil.getLoginIdAsString();
            JSONObject cached = SsoClientController.getUserInfoCache().get(userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", userId);

            if (cached != null) {
                // SSO 缓存中有完整信息
                // SSO 登录账号即邮箱，username 直接映射 email（SSO 不返回 username 字段）
                result.put("username", cached.getStr("email", ""));
                result.put("nickname", cached.getStr("nickname", "用户"));
                result.put("avatar", cached.getStr("avatar", ""));
                result.put("email", cached.getStr("email", ""));
            } else {
                result.put("username", userId);
                result.put("nickname", "用户");
                result.put("avatar", "");
                result.put("email", "");
            }
            return R.ok(result);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return R.fail("获取用户信息失败");
        }
    }

    @PostMapping("/logout")
    public R<Map<String, String>> logout() {
        if (StpUtil.isLogin()) {
            try {
                String userId = StpUtil.getLoginIdAsString();
                SsoClientController.getUserInfoCache().remove(userId);
                SsoClientController.getSsoTokenCache().remove(userId);
            } catch (Exception e) {
                log.warn("清除用户缓存异常（忽略）", e);
            }
            try {
                StpUtil.logout();
            } catch (Exception e) {
                log.warn("JNClub 本地 session 退出异常（忽略）", e);
            }
        }

        String encodedBack = URLEncoder.encode(frontendUrl, StandardCharsets.UTF_8);
        String ssoLogoutUrl = serverUrl + "/logout?redirect=" + encodedBack;

        Map<String, String> data = new HashMap<>();
        data.put("ssoLogoutUrl", ssoLogoutUrl);
        return R.ok(data);
    }
}
