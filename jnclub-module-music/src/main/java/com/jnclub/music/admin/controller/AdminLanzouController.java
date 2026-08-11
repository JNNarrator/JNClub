package com.jnclub.music.admin.controller;

import com.jnclub.music.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.jnclub.music.common.enums.ErrorCode;
import com.jnclub.music.common.exception.BusinessException;
import com.jnclub.music.track.service.TrackCacheService;
import com.jnclub.music.lanzou.LanzouApiClient;
import com.jnclub.music.lanzou.LanzouSessionException;
import com.jnclub.music.lanzou.LanzouUidVei;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台管理：蓝奏云会话入口。
 * 提供两种更新方式：
 *   1) POST /cookie 直接粘贴浏览器 Cookie
 *   2) POST /login  账号密码自动登录
 * 均会立即用 getUidVei 探活，并把结果写入本地 Cookie 缓存。
 */
@RestController
@RequestMapping("/api/v1/admin/lanzou")
public class AdminLanzouController {

    private static final Logger log = LoggerFactory.getLogger(AdminLanzouController.class);

    private final LanzouApiClient lanzouClient;
    private final TrackCacheService cacheService;

    public AdminLanzouController(LanzouApiClient lanzouClient, TrackCacheService cacheService) {
        this.lanzouClient = lanzouClient;
        this.cacheService = cacheService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            LanzouUidVei uv = lanzouClient.getUidVei();
            log.info("蓝奏云状态: uid={}, 开始 listFiles 探活...", uv.uid());
            // 调用 listFiles 才能真正验证 uid/vei 可用，防止"登录假成功"
            lanzouClient.listFiles("-1", 1);
            log.info("蓝奏云状态: listFiles 成功, 会话有效 uid={}", uv.uid());
            data.put("authenticated", true);
            data.put("uid", uv.uid());
        } catch (RuntimeException e) {
            data.put("authenticated", false);
            data.put("reason", e.getMessage());
        }
        return ApiResponse.success(data);
    }

    @PostMapping("/cookie")
    public ApiResponse<Map<String, Object>> updateCookie(@Valid @RequestBody CookieRequest req) {
        try {
            lanzouClient.setSessionCookie(req.cookie().trim());
            lanzouClient.saveCookieCache();
            LanzouUidVei uv = lanzouClient.getUidVei();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", true);
            data.put("uid", uv.uid());
            return ApiResponse.success(data);
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Cookie 无效: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@Valid @RequestBody LoginRequest req) {
        try {
            String username = req.username().trim();
            log.info("蓝奏云登录: username={}, 开始登录...", username);
            lanzouClient.login(username, req.password());
            log.info("蓝奏云登录: login() 成功, 保存 Cookie...");
            lanzouClient.saveCookieCache();
            LanzouUidVei uv = lanzouClient.getUidVei();
            log.info("蓝奏云登录: getUidVei 得到 uid={}, 开始 listFiles 探活...", uv.uid());
            // 调用 listFiles 才能真正验证会话可用，防止"登录假成功"
            lanzouClient.listFiles("-1", 1);
            log.info("蓝奏云登录: listFiles 成功, 登录有效 uid={}", uv.uid());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", true);
            data.put("uid", uv.uid());
            return ApiResponse.success(data);
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "登录失败: " + e.getMessage());
        }
    }

    public record CookieRequest(@NotBlank String cookie) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    @Operation(summary = "刷新歌曲直链缓存", description = "手动触发全量刷新所有歌曲的播放直链")
    @PostMapping("/refresh-cache")
    public ApiResponse<String> refreshCache() {
        cacheService.manualRefresh();
        return ApiResponse.success("缓存刷新已触发");
    }

}
