package com.jnclub.clipboard.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设置同步白名单测试。
 *
 * <p>这套规则决定「哪些设置允许跨设备旅行」，写错的两个方向都有代价：
 * 放过设备私有键，两台机器的窗口尺寸会互相覆盖；
 * 放过密码键，凭据会以明文落进服务端数据库。
 * 规则与桌面端 Rust 的 {@code is_setting_sync_eligible} 是同一套语义。
 */
class ClipboardSettingWhitelistTest {

    @Test
    void 普通界面设置可同步() {
        assertTrue(ClipboardSyncService.isSettingSyncable("app.theme"));
        assertTrue(ClipboardSyncService.isSettingSyncable("app.language"));
        assertTrue(ClipboardSyncService.isSettingSyncable("app.privacy_protection"));
        assertTrue(ClipboardSyncService.isSettingSyncable("app.persistent"));
    }

    @Test
    void 设备私有键被拒() {
        // 窗口尺寸：每台机器自己的屏幕决定
        assertFalse(ClipboardSyncService.isSettingSyncable("app.window_width"));
        assertFalse(ClipboardSyncService.isSettingSyncable("app.window_height"));
        // 匿名 ID：同步过去等于两台设备变成同一台
        assertFalse(ClipboardSyncService.isSettingSyncable("app.anon_id"));
        // 本机路径与启动行为
        assertFalse(ClipboardSyncService.isSettingSyncable("app.last_edge_dock"));
        assertFalse(ClipboardSyncService.isSettingSyncable("app.silent_start"));
    }

    @Test
    void 设备私有前缀被拒() {
        assertFalse(ClipboardSyncService.isSettingSyncable("cloud_sync_cursor"));
        assertFalse(ClipboardSyncService.isSettingSyncable("cloud_sync_webdav_url"));
        assertFalse(ClipboardSyncService.isSettingSyncable("clipboard_last_seq"));
    }

    @Test
    void 敏感键被拒() {
        assertFalse(ClipboardSyncService.isSettingSyncable("mqtt_password"));
        assertFalse(ClipboardSyncService.isSettingSyncable("cloud_sync_api_key"));
        assertFalse(ClipboardSyncService.isSettingSyncable("cloud_sync_webdav_password"));
        assertFalse(ClipboardSyncService.isSettingSyncable("e2e_key"));
    }

    @Test
    void 已删功能的遗留键被拒() {
        // 老库里还有这些行，但不该因为没人读就开始跨端旅行
        assertFalse(ClipboardSyncService.isSettingSyncable("mqtt_client_id"));
        assertFalse(ClipboardSyncService.isSettingSyncable("file_server_port"));
        assertFalse(ClipboardSyncService.isSettingSyncable("file_transfer_path"));
    }

    @Test
    void 空键被拒() {
        assertFalse(ClipboardSyncService.isSettingSyncable(null));
        assertFalse(ClipboardSyncService.isSettingSyncable(""));
        assertFalse(ClipboardSyncService.isSettingSyncable("   "));
    }

    @Test
    void 前缀匹配不能误伤同名前缀的普通键() {
        // cloud_sync 是前缀，但 app.cloud_sync_note 这种以它开头的键不存在；
        // 真正要防的是「键里包含 cloud_sync」被误判——这里确认只有前缀命中才拒
        assertTrue(ClipboardSyncService.isSettingSyncable("app.cloud_sync_hint_shown"));
    }
}
