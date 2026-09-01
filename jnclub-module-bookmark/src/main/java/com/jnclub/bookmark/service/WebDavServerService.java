package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.WebDavServer;
import com.jnclub.bookmark.mapper.WebDavServerMapper;
import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WebDAV 站点管理服务 — CRUD + 密码 AES 加密存储
 * <p>
 * 密码使用配置密钥（jnclub.webdav.crypto-key，MD5 派生 32 字节）AES 加密落库，
 * 列表/详情接口一律不回传明文，仅在使用时于内存解密。
 */
@Slf4j
@Service
public class WebDavServerService extends ServiceImpl<WebDavServerMapper, WebDavServer> {

    @Value("${jnclub.webdav.crypto-key:jnclub-webdav-2026}")
    private String cryptoKey;

    private final WebDavServerMapper webDavServerMapper;

    public WebDavServerService(WebDavServerMapper webDavServerMapper) {
        this.webDavServerMapper = webDavServerMapper;
    }

    // ============================================================
    // CRUD
    // ============================================================

    /** 我的站点列表（password 置空） */
    public List<WebDavServer> listMine() {
        String userId = StpUtil.getLoginIdAsString();
        List<WebDavServer> list = list(new LambdaQueryWrapper<WebDavServer>()
                .eq(WebDavServer::getUserId, userId)
                .orderByAsc(WebDavServer::getId));
        for (WebDavServer s : list) s.setPassword(null);
        return list;
    }

    /** 新增（密码加密） */
    public WebDavServer create(WebDavServer server) {
        String userId = StpUtil.getLoginIdAsString();
        validate(server);
        server.setId(null);
        server.setUserId(userId);
        server.setPassword(encrypt(server.getPassword()));
        save(server);
        server.setPassword(null);
        return server;
    }

    /** 更新（password 传空 = 不变） */
    public void update(Long id, WebDavServer server) {
        String userId = StpUtil.getLoginIdAsString();
        WebDavServer exist = getOwned(id, userId);
        validate(server);
        exist.setName(server.getName());
        exist.setUrl(server.getUrl());
        exist.setUsername(server.getUsername());
        exist.setNotes(server.getNotes());
        if (server.getPassword() != null && !server.getPassword().isBlank()) {
            exist.setPassword(encrypt(server.getPassword()));
        }
        updateById(exist);
    }

    /** 删除 */
    public void delete(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        WebDavServer exist = getOwned(id, userId);
        removeById(exist.getId());
    }

    /** 获取所属站点（越权防护）；如需密码解密走 decryptPassword */
    public WebDavServer getOwned(Long id, String userId) {
        WebDavServer server = getById(id);
        if (server == null || !server.getUserId().equals(userId)) {
            throw new BizException("站点不存在");
        }
        return server;
    }

    /** 解密密码（空则返回空串） */
    public String decryptPassword(WebDavServer server) {
        return decrypt(server.getPassword());
    }

    /** 站点检测连接（PROPFIND 根目录），返回目录项数或报错 */
    public int test(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        WebDavServer server = getOwned(id, userId);
        WebDavClient client = buildClient(server);
        try {
            return client.list("").size();
        } catch (BizException e) {
            throw new BizException("连接失败：" + e.getMessage());
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private void validate(WebDavServer server) {
        if (server.getUrl() == null || server.getUrl().isBlank()) {
            throw new BizException("请填写 WebDAV 地址");
        }
        String u = server.getUrl().trim().toLowerCase();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            throw new BizException("WebDAV 地址需以 http:// 或 https:// 开头");
        }
    }

    /** 由站点记录构建 WebDAV 客户端（内存中解密密码） */
    public WebDavClient buildClient(WebDavServer server) {
        return new WebDavClient(server.getUrl(), server.getUsername(), decrypt(server.getPassword()));
    }

    private AES aes() {
        byte[] key = SecureUtil.md5(cryptoKey).getBytes(StandardCharsets.UTF_8);
        return SecureUtil.aes(key);
    }

    private String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        return aes().encryptHex(plain);
    }

    private String decrypt(String cipherHex) {
        if (cipherHex == null || cipherHex.isBlank()) return "";
        try {
            return aes().decryptStr(cipherHex);
        } catch (Exception e) {
            log.warn("WebDAV 密码解密失败: {}", e.getMessage());
            return "";
        }
    }
}
