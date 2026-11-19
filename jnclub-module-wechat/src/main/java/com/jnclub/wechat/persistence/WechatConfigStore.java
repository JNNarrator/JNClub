package com.jnclub.wechat.persistence;

import com.jnclub.wechat.config.WechatUserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每用户微信通道配置的读写（{@code t_wechat_config}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatConfigStore {

    private final JdbcTemplate jdbcTemplate;

    /** 读取配置；没有记录时返回默认值（enabled=true、白名单为空）。 */
    public WechatUserConfig load(String userId) {
        WechatUserConfig config = new WechatUserConfig();
        config.setUserId(userId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT enabled, whitelist, notify_on_copy FROM t_wechat_config WHERE user_id = ?", userId);
            if (rows.isEmpty()) {
                return config;
            }
            Map<String, Object> row = rows.get(0);
            config.setEnabled(asBool(row.get("enabled"), true));
            config.setWhitelist(asString(row.get("whitelist")));
            config.setNotifyOnCopy(asBool(row.get("notify_on_copy"), true));
        } catch (Exception e) {
            log.warn("读取微信通道配置失败 user={}：{}", userId, e.getMessage());
        }
        return config;
    }

    public void save(WechatUserConfig config) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_wechat_config (user_id, enabled, clipboard_bridge, whitelist, notify_on_copy, updated_at)
                    VALUES (?, ?, 1, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        enabled = VALUES(enabled), whitelist = VALUES(whitelist),
                        notify_on_copy = VALUES(notify_on_copy), updated_at = VALUES(updated_at)
                    """,
                    config.getUserId(),
                    config.isEnabled() ? 1 : 0,
                    config.getWhitelist(),
                    config.isNotifyOnCopy() ? 1 : 0,
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("保存微信通道配置失败 user={}：{}", config.getUserId(), e.getMessage());
        }
    }

    /**
     * 把某个微信 userId 追加进白名单（幂等）。
     *
     * <p>扫码登录后用它把本人加进去：bot 属于登录者本人，
     * 所以"默认允许谁"的答案就是"登录的那个人"。这样通道开箱即用，
     * 又不至于对陌生人敞开。
     */
    public void addWhitelistEntry(String userId, String wechatUserId) {
        if (wechatUserId == null || wechatUserId.isBlank()) {
            return;
        }
        WechatUserConfig config = load(userId);
        List<String> entries = splitWhitelist(config.getWhitelist());
        if (entries.contains(wechatUserId.trim())) {
            return;
        }
        entries.add(wechatUserId.trim());
        config.setWhitelist(String.join(",", entries));
        save(config);
    }

    /** 解析逗号分隔的白名单。 */
    public static List<String> splitWhitelist(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** 已登录（有凭据）的用户列表，用于启动时逐个恢复通道。 */
    public List<String> usersWithCredentials() {
        List<String> users = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id FROM t_wechat_credentials");
            for (Map<String, Object> row : rows) {
                Object value = row.get("user_id");
                if (value != null) {
                    users.add(String.valueOf(value));
                }
            }
        } catch (Exception e) {
            log.warn("读取已登录用户失败：{}", e.getMessage());
        }
        return users;
    }

    private static boolean asBool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return fallback;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
