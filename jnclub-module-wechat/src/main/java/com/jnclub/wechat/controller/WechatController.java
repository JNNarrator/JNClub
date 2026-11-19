package com.jnclub.wechat.controller;

import com.jnclub.common.model.R;
import com.jnclub.wechat.config.WechatProperties;
import com.jnclub.wechat.runtime.WechatChannelManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信通道的对外接口，供 jn-clipboard 设置页使用。
 *
 * <p>前缀 {@code /api/wechat}，不在 {@code /api/v1/**} 匿名白名单内，
 * 并显式 {@code StpUtil.checkLogin()}——原因与 ClipboardController 相同：
 * 前缀规则将来被改动时，鉴权不会跟着一起失效。
 *
 * <p>注意登录态：这些接口按当前登录用户鉴权，但通道本身是单例的
 * （单用户自用）。多用户场景需要把通道改为按 userId 隔离。
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat")
@RequiredArgsConstructor
public class WechatController {

    private final WechatChannelManager channelManager;
    private final WechatProperties properties;

    /** 通道当前状态：前端据此决定显示二维码还是"已连接"。 */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        Map<String, Object> data = new HashMap<>();
        data.put("state", channelManager.state().name());
        data.put("connected", channelManager.isConnected());
        data.put("userId", channelManager.loggedInUserId());
        data.put("error", channelManager.lastError());
        data.put("waitingScan", channelManager.pendingQrcode() != null);
        data.put("autoStart", properties.isAutoStart());
        data.put("notifyOnCopy", properties.isNotifyOnCopy());
        data.put("whitelist", properties.whitelistText());
        return R.ok(data);
    }

    /**
     * 请求登录二维码。
     *
     * <p>返回 data 是二维码内容字符串（前端自己渲染成图），
     * 已经登录则返回 {@code connected: true} 而不带二维码。
     */
    @PostMapping("/qrcode")
    public R<Map<String, Object>> qrcode() {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        Map<String, Object> data = new HashMap<>();
        try {
            String content = channelManager.requestQrcode();
            if (content == null) {
                data.put("connected", true);
                data.put("message", "通道已连接");
            } else {
                data.put("connected", false);
                data.put("qrcodeContent", content);
                // 直接给出可显示的图片：前端因此不必引入二维码库，
                // 尺寸与留白也由后端统一控制。
                data.put("qrcodeImage", toPngDataUrl(content));
            }
            return R.ok(data);
        } catch (Exception e) {
            log.error("获取二维码失败：{}", e.getMessage(), e);
            return R.fail("获取二维码失败：" + e.getMessage());
        }
    }

    /** 把二维码内容渲染成 `data:image/png;base64,...`。失败时返回 null，前端会退回到只显示内容字符串。 */
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

    /** 断开通道（不清除凭据，重启后仍可恢复）。 */
    @PostMapping("/stop")
    public R<Void> stop() {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        channelManager.stop();
        return R.ok();
    }

    /** 恢复通道（用已保存的凭据）。 */
    @PostMapping("/resume")
    public R<Map<String, Object>> resume() {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        boolean ok = channelManager.resume();
        Map<String, Object> data = new HashMap<>();
        data.put("resumed", ok);
        data.put("state", channelManager.state().name());
        return R.ok(data);
    }

    /** 主动发一条消息到指定微信对话，用于联调。 */
    @PostMapping("/send")
    public R<Map<String, Object>> send(@RequestParam String toUserId, @RequestParam String content) {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        Map<String, Object> data = new HashMap<>();
        try {
            boolean ok = channelManager.sendText(toUserId, content);
            data.put("sent", ok);
            return R.ok(data);
        } catch (Exception e) {
            return R.fail("发送失败：" + e.getMessage());
        }
    }
}
