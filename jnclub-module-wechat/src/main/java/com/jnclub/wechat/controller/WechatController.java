package com.jnclub.wechat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.jnclub.common.model.R;
import com.jnclub.wechat.config.WechatUserConfig;
import com.jnclub.wechat.persistence.WechatConfigStore;
import com.jnclub.wechat.runtime.WechatChannelManager;
import com.jnclub.wechat.runtime.WechatChannelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信通道接口。所有操作都作用于<b>当前登录用户自己的</b>通道。
 *
 * <p>前缀 {@code /api/wechat} 不在 {@code /api/v1/**} 匿名白名单内，
 * 并显式 {@code StpUtil.checkLogin()}——原因与 ClipboardController 相同：
 * 前缀规则将来被改动时，鉴权不会跟着一起失效。
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat")
@RequiredArgsConstructor
public class WechatController {

    private final WechatChannelRegistry registry;
    private final WechatConfigStore configStore;

    /** 当前登录用户自己的通道状态。 */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        WechatChannelManager channel = registry.getOrCreate(userId);
        WechatUserConfig config = configStore.load(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("state", channel.state().name());
        data.put("connected", channel.isConnected());
        data.put("userId", channel.loggedInWechatUserId());
        data.put("error", channel.lastError());
        data.put("waitingScan", channel.pendingQrcode() != null);
        data.put("notifyOnCopy", config.isNotifyOnCopy());
        data.put("whitelist", config.getWhitelist());
        data.put("enabled", config.isEnabled());
        return R.ok(data);
    }

    /**
     * 请求登录二维码（当前用户扫码登录自己的微信号）。
     *
     * <p>返回 data.qrcodeImage 是 {@code data:image/png;base64,...}，
     * 前端因此不必引入二维码库。
     */
    @PostMapping("/qrcode")
    public R<Map<String, Object>> qrcode() {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> data = new HashMap<>();
        try {
            String content = registry.getOrCreate(userId).requestQrcode();
            if (content == null) {
                data.put("connected", true);
                data.put("message", "通道已连接");
            } else {
                data.put("connected", false);
                data.put("qrcodeContent", content);
                data.put("qrcodeImage", toPngDataUrl(content));
            }
            return R.ok(data);
        } catch (Exception e) {
            log.error("[{}] 获取二维码失败：{}", userId, e.getMessage(), e);
            return R.fail("获取二维码失败：" + e.getMessage());
        }
    }

    /** 把二维码内容渲染成 {@code data:image/png;base64,...}。 */
    private static String toPngDataUrl(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 220, 220);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("二维码图片生成失败：{}", e.getMessage());
            return null;
        }
    }

    /** 断开当前用户的通道（保留凭据，重启后仍可恢复）。 */
    @PostMapping("/stop")
    public R<Void> stop() {
        StpUtil.checkLogin();
        registry.getOrCreate(StpUtil.getLoginIdAsString()).stop();
        return R.ok();
    }

    /** 用已保存的凭据恢复当前用户的通道。 */
    @PostMapping("/resume")
    public R<Map<String, Object>> resume() {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        WechatChannelManager channel = registry.getOrCreate(userId);
        boolean ok = channel.resume();
        Map<String, Object> data = new HashMap<>();
        data.put("resumed", ok);
        data.put("state", channel.state().name());
        return R.ok(data);
    }

    /** 更新当前用户的通道设置（白名单 / 复制提醒开关）。 */
    @PostMapping("/settings")
    public R<Map<String, Object>> updateSettings(@RequestParam(required = false) String whitelist,
                                                 @RequestParam(required = false) Boolean notifyOnCopy) {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        WechatUserConfig config = configStore.load(userId);
        if (whitelist != null) {
            config.setWhitelist(whitelist);
        }
        if (notifyOnCopy != null) {
            config.setNotifyOnCopy(notifyOnCopy);
        }
        configStore.save(config);
        return status();
    }

    /** 主动发一条消息，用于联调。 */
    @PostMapping("/send")
    public R<Map<String, Object>> send(@RequestParam String toUserId, @RequestParam String content) {
        StpUtil.checkLogin();
        Map<String, Object> data = new HashMap<>();
        try {
            boolean ok = registry.getOrCreate(StpUtil.getLoginIdAsString()).sendText(toUserId, content);
            data.put("sent", ok);
            return R.ok(data);
        } catch (Exception e) {
            return R.fail("发送失败：" + e.getMessage());
        }
    }
}
