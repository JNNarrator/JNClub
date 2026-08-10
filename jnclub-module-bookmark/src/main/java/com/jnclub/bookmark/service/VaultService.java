package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.entity.Vault;
import com.jnclub.bookmark.mapper.VaultMapper;
import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 密码库服务 — AES 加密存储 + CRUD + 软删除（回收站）
 * <p>
 * 密码字段以 AES 密文入库；列表查询不返回密码，详情接口才解密返回。
 * 密钥来自配置 jnclub.vault.crypto-key，生产环境务必替换为独立随机密钥。
 */
@Slf4j
@Service
public class VaultService extends ServiceImpl<VaultMapper, Vault> {

    @Value("${jnclub.vault.crypto-key:jnclub-vault-2026}")
    private String cryptoKey;

    /** 派生固定长度 AES 密钥（MD5 → 32 字节，兼容任意长度配置） */
    private AES aes() {
        byte[] key = SecureUtil.md5(cryptoKey).getBytes(StandardCharsets.UTF_8);
        return SecureUtil.aes(key);
    }

    // ============================================================
    // 列表 / 详情
    // ============================================================

    /** 目录内列表：密码置空（不返回密文） */
    public List<Vault> list(Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        List<Vault> list = list(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getDirectoryId, directoryId)
                .eq(Vault::getUserId, userId)
                .eq(Vault::getDeleted, 0)
                .orderByAsc(Vault::getSortOrder));
        list.forEach(v -> v.setPassword(null));
        return list;
    }

    /** 详情：解密返回密码明文 */
    public Vault getDetail(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Vault vault = getById(id);
        if (vault == null || !vault.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        if (vault.getPassword() != null && !vault.getPassword().isBlank()) {
            try {
                vault.setPassword(aes().decryptStr(vault.getPassword()));
            } catch (Exception e) {
                log.warn("密码解密失败 id={}: {}", id, e.getMessage());
                vault.setPassword(null);
            }
        }
        return vault;
    }

    // ============================================================
    // 增删改
    // ============================================================

    public Vault create(Vault vault) {
        String userId = StpUtil.getLoginIdAsString();
        if (vault.getName() == null || vault.getName().isBlank()) {
            throw new BizException("条目名称不能为空");
        }
        vault.setUserId(userId);
        vault.setPassword(encrypt(vault.getPassword()));
        save(vault);
        vault.setPassword(null);
        return vault;
    }

    public void update(Long id, Vault vault) {
        String userId = StpUtil.getLoginIdAsString();
        Vault existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        existing.setName(vault.getName());
        existing.setUsername(vault.getUsername());
        existing.setUrl(vault.getUrl());
        existing.setNotes(vault.getNotes());
        // 密码：传入为空视为不变；传入非空则更新（重新加密）
        if (vault.getPassword() != null && !vault.getPassword().isBlank()) {
            existing.setPassword(encrypt(vault.getPassword()));
        }
        updateById(existing);
    }

    /** 软删除：进入回收站 */
    public void delete(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Vault vault = getById(id);
        if (vault == null || !vault.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        vault.setDeleted(1);
        updateById(vault);
    }

    @Transactional
    public void updateSortOrder(List<Map<String, Object>> sortList) {
        String userId = StpUtil.getLoginIdAsString();
        List<Vault> toUpdate = new ArrayList<>();
        for (Map<String, Object> item : sortList) {
            Long id = Long.parseLong(item.get("id").toString());
            Integer sortOrder = Integer.parseInt(item.get("sortOrder").toString());
            Vault vault = getById(id);
            if (vault != null && vault.getUserId().equals(userId)) {
                vault.setSortOrder(sortOrder);
                toUpdate.add(vault);
            }
        }
        if (!toUpdate.isEmpty()) updateBatchById(toUpdate);
    }

    // ============================================================
    // 回收站支持（供 RecycleService 复用）
    // ============================================================

    /** 回收站列表 */
    public List<Vault> listRecycle(String userId) {
        return list(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId)
                .eq(Vault::getDeleted, 1)
                .orderByDesc(Vault::getCreateTime));
    }

    /** 从回收站恢复 */
    public void restore(Long id, String userId) {
        Vault vault = getById(id);
        if (vault == null || !vault.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        if (vault.getDeleted() == null || vault.getDeleted() != 1) {
            throw new BizException("条目不在回收站中");
        }
        vault.setDeleted(0);
        updateById(vault);
    }

    /** 永久删除（回收站清空/到期清理用） */
    public void permanentlyDelete(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Vault vault = getById(id);
        if (vault == null || !vault.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        removeById(id);
    }

    /** 无鉴权永久删除（回收站定时清理用） */
    public void purgeByIdNoAuth(Long id) {
        Vault vault = getById(id);
        if (vault == null) return;
        removeById(id);
    }

    // ============================================================
    // 工具
    // ============================================================

    private String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        return aes().encryptHex(plain);
    }
}
