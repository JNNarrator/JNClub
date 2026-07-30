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
    public R<Object> getUserinfo() {
        try {
            if (!StpUtil.isLogin()) {
                return R.fail(401, "未登录");
            }
            String userId = StpUtil.getLoginIdAsString();
            JSONObject cached = SsoClientController.getUserInfoCache().get(userId);
            if (cached != null) {
                return R.ok(cached);
            }
            JSONObject basicInfo = new JSONObject();
            basicInfo.set("userId", userId);
            return R.ok(basicInfo);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return R.fail("获取用户信息失败");
        }
    }

    @PostMapping("/logout")
    public R<Map<String, String>> logout() {
        // 清除 JNClub 本地 session
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

        // 构造 SSO 退出 URL，back 参数必须 URL 编码
        String encodedBack = URLEncoder.encode(frontendUrl, StandardCharsets.UTF_8);
        String ssoLogoutUrl = serverUrl + "/sso/signout?back=" + encodedBack;

        Map<String, String> data = new HashMap<>();
        data.put("ssoLogoutUrl", ssoLogoutUrl);
        return R.ok(data);
    }
}
