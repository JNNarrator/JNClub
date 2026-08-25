package com.jnclub.bookmark.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jnclub.bookmark.crypto.VaultCrypto;
import com.jnclub.bookmark.entity.Vault;
import com.jnclub.bookmark.entity.VaultMeta;
import com.jnclub.bookmark.entity.Directory;
import com.jnclub.bookmark.entity.VaultTotp;
import com.jnclub.bookmark.mapper.VaultMapper;
import com.jnclub.bookmark.mapper.VaultMetaMapper;
import com.jnclub.bookmark.mapper.DirectoryMapper;
import com.jnclub.bookmark.mapper.VaultTotpMapper;
import com.jnclub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 密码库服务 — 主密钥加密存储 + CRUD + 软删除（回收站）+ 密码健康检查
 * <p>
 * 加密体系两级：
 * 1. 未设置主密钥（兼容旧数据）：沿用配置 jnclub.vault.crypto-key（MD5 派生），存量数据无感。
 * 2. 已设置主密钥：PBKDF2(主密钥, salt, 10万次) 派生 32 字节 AES 密钥，
 *    密钥仅存内存（30 分钟空闲过期），永不落库；设置/修改时全量重加密迁移。
 * 密码指纹（SHA-256）存库用于同用户重复密码检测（不解密可比）；强度评分仅提示不拦截。
 */
@Slf4j
@Service
public class VaultService extends ServiceImpl<VaultMapper, Vault> {

    @Value("${jnclub.vault.crypto-key:jnclub-vault-2026}")
    private String cryptoKey;

    private final VaultMetaMapper vaultMetaMapper;

    private final DirectoryMapper directoryMapper;
    private final VaultTotpMapper vaultTotpMapper;

    public VaultService(VaultMetaMapper vaultMetaMapper, DirectoryMapper directoryMapper,
                        VaultTotpMapper vaultTotpMapper) {
        this.vaultMetaMapper = vaultMetaMapper;
        this.directoryMapper = directoryMapper;
        this.vaultTotpMapper = vaultTotpMapper;
    }

    /** 主密钥空闲过期（毫秒）：30 分钟 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    /** 内存解锁会话：userId → { 派生密钥, 最后访问时间 } */
    private static final class UnlockSession {
        byte[] key;
        long lastAccess;
    }

    private final Map<String, UnlockSession> sessions = new ConcurrentHashMap<>();

    // ============================================================
    // 主密钥管理
    // ============================================================

    /** 状态：{ configured: 是否已设置主密钥, unlocked: 当前是否已解锁 } */
    public Map<String, Object> masterKeyStatus() {
        String userId = StpUtil.getLoginIdAsString();
        VaultMeta meta = getMeta(userId);
        boolean configured = meta != null;
        boolean unlocked = configured && sessions.containsKey(userId);
        Map<String, Object> status = new HashMap<>();
        status.put("configured", configured);
        status.put("unlocked", unlocked);
        return status;
    }

    /**
     * 设置/修改主密钥（已设置需先验证旧密钥）→ 生成新盐 + keyCheck + 全量重加密迁移
     */
    @Transactional
    public void setMasterKey(String oldMasterKey, String newMasterKey) {
        String userId = StpUtil.getLoginIdAsString();
        if (newMasterKey == null || newMasterKey.length() < 8) {
            throw new BizException("主密钥至少 8 位");
        }
        VaultMeta meta = getMeta(userId);
        if (meta != null) {
            // 修改：先验证旧主密钥
            if (oldMasterKey == null || !verifyMasterKey(userId, meta, oldMasterKey)) {
                throw new BizException("原主密钥不正确");
            }
            // 未解锁状态修改需先解锁（用旧密钥派生后重加密）
            if (!sessions.containsKey(userId)) {
                sessions.put(userId, newSession(derive(meta, oldMasterKey)));
            }
        }

        // 新盐 + 新派生密钥 + keyCheck
        String salt = VaultCrypto.generateSalt();
        byte[] newKey = VaultCrypto.deriveKey(newMasterKey, salt, VaultCrypto.PBKDF2_ITERATIONS);
        String keyCheck = VaultCrypto.encrypt(newKey, VaultCrypto.KEY_CHECK_PLAIN);

        // 重加密迁移全部条目（含回收站中的，保证恢复后仍可解密）
        reencryptAll(userId, newKey);

        VaultMeta updated = new VaultMeta();
        updated.setUserId(userId);
        updated.setSalt(salt);
        updated.setIterations(VaultCrypto.PBKDF2_ITERATIONS);
        updated.setKeyVersion(1);
        updated.setKdf("PBKDF2-SHA256");
        updated.setKeyCheck(keyCheck);
        if (meta == null) {
            vaultMetaMapper.insert(updated);
        } else {
            vaultMetaMapper.updateById(updated);
        }

        // 更新内存会话为最新密钥
        sessions.put(userId, newSession(newKey));
    }

    /** 解锁：派生密钥解密 keyCheck 校验 */
    public void unlock(String masterKey) {
        String userId = StpUtil.getLoginIdAsString();
        VaultMeta meta = getMeta(userId);
        if (meta == null) {
            throw new BizException("尚未设置主密钥");
        }
        if (!verifyMasterKey(userId, meta, masterKey)) {
            throw new BizException("主密钥不正确");
        }
        sessions.put(userId, newSession(derive(meta, masterKey)));
    }

    /** 锁定：清除内存密钥 */
    public void lock() {
        String userId = StpUtil.getLoginIdAsString();
        sessions.remove(userId);
    }

    /**
     * 遗忘重置：双重确认（confirm=RESET + resetCode 必须等于 userId 后 8 位）
     * 清空全部条目 + 元数据，重新开始
     */
    @Transactional
    public void reset(String confirm, String resetCode) {
        String userId = StpUtil.getLoginIdAsString();
        if (!"RESET".equals(confirm)) {
            throw new BizException("请输入确认码 RESET 以确认重置");
        }
        String expected = userId.length() > 8 ? userId.substring(userId.length() - 8) : userId;
        if (resetCode == null || !resetCode.equalsIgnoreCase(expected)) {
            throw new BizException("重置验证码不正确");
        }
        // 永久删除全部条目（含回收站）
        List<Vault> all = list(new LambdaQueryWrapper<Vault>().eq(Vault::getUserId, userId));
        if (!all.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Vault v : all) ids.add(v.getId());
            removeBatchByIds(ids);
        }
        vaultMetaMapper.deleteById(userId);
        sessions.remove(userId);
    }

    /** 健康检查（需解锁）：弱/重复密码列表 */
    public Map<String, Object> checkHealth() {
        String userId = StpUtil.getLoginIdAsString();
        requireUnlocked(userId);

        List<Vault> list = list(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId)
                .eq(Vault::getDeleted, 0));

        Map<String, String> fingerprintOf = new HashMap<>();
        List<Map<String, Object>> weakList = new ArrayList<>();
        List<Map<String, Object>> duplicateList = new ArrayList<>();
        Map<String, Object> seen = new HashMap<>();

        byte[] key = currentKey(userId);
        for (Vault v : list) {
            if (v.getPassword() == null || v.getPassword().isBlank()) continue;
            String plain;
            try {
                plain = decryptWith(key, v.getPassword());
            } catch (Exception e) {
                continue;
            }
            if (plain == null || plain.isEmpty()) continue;

            int score = VaultCrypto.strengthScore(plain);
            if (score < 60) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", v.getId());
                item.put("name", v.getName());
                item.put("score", score);
                weakList.add(item);
            }

            String fp = VaultCrypto.fingerprint(plain);
            if (fp != null) {
                fingerprintOf.put(String.valueOf(v.getId()), fp);
                if (seen.containsKey(fp)) {
                    duplicateList.add(Map.of("id", v.getId(), "name", v.getName(), "sameAs", seen.get(fp)));
                } else {
                    seen.put(fp, v.getName());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("weak", weakList);
        result.put("duplicates", duplicateList);
        return result;
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

    /** 分页列出目录内密码条目：密码置空 */
    public Map<String, Object> pageList(Long directoryId, long page, long size) {
        String userId = StpUtil.getLoginIdAsString();
        var p = page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<Vault>()
                        .eq(Vault::getDirectoryId, directoryId)
                        .eq(Vault::getUserId, userId)
                        .eq(Vault::getDeleted, 0)
                        .orderByAsc(Vault::getSortOrder));
        p.getRecords().forEach(v -> v.setPassword(null));
        Map<String, Object> result = new HashMap<>();
        result.put("items", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 详情：解密返回密码明文（已设置主密钥需先解锁） */
    public Vault getDetail(Long id) {
        String userId = StpUtil.getLoginIdAsString();
        Vault vault = getById(id);
        if (vault == null || !vault.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        if (vault.getPassword() != null && !vault.getPassword().isBlank()) {
            try {
                vault.setPassword(decryptWith(currentKey(userId), vault.getPassword()));
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
        requireUnlocked(userId);
        vault.setUserId(userId);
        // 指纹对明文计算（健康检查不解密可比），再加密入库
        vault.setPasswordFingerprint(VaultCrypto.fingerprint(vault.getPassword()));
        vault.setPassword(encryptWith(currentKey(userId), vault.getPassword()));
        save(vault);
        vault.setPassword(null);
        return vault;
    }

    public void update(Long id, Vault vault) {
        String userId = StpUtil.getLoginIdAsString();
        requireUnlocked(userId);
        Vault existing = getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        existing.setName(vault.getName());
        existing.setUsername(vault.getUsername());
        existing.setUrl(vault.getUrl());
        existing.setNotes(vault.getNotes());
        // 密码：传入为空视为不变；传入非空则更新（重新加密 + 重算指纹）
        if (vault.getPassword() != null && !vault.getPassword().isBlank()) {
            existing.setPassword(encryptWith(currentKey(userId), vault.getPassword()));
            existing.setPasswordFingerprint(VaultCrypto.fingerprint(vault.getPassword()));
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

    /** 移动密码条目到其他目录（type=5 目录校验；需主密钥解锁） */
    public void move(Long id, Long directoryId) {
        String userId = StpUtil.getLoginIdAsString();
        requireUnlocked(userId);
        Vault vault = getById(id);
        if (vault == null || !vault.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        if (directoryId == null) {
            throw new BizException("请选择目标目录");
        }
        checkDirOwnership(directoryId, userId);
        if (vault.getDirectoryId() != null && vault.getDirectoryId().equals(directoryId)) {
            throw new BizException("已在该目录中");
        }
        vault.setDirectoryId(directoryId);
        updateById(vault);
    }

    /** 目标目录归属 + type 校验（密码库目录 type=5，模式同 CloudDiskService.checkDirOwnership） */
    private void checkDirOwnership(Long directoryId, String userId) {
        Directory dir = directoryMapper.selectById(directoryId);
        if (dir == null || !dir.getUserId().equals(userId)) {
            throw new BizException("目录不存在");
        }
        if (dir.getType() == null || dir.getType() != 5) {
            throw new BizException("目标目录不是密码库目录");
        }
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
    // 密钥与会话
    // ============================================================

    private VaultMeta getMeta(String userId) {
        return vaultMetaMapper.selectById(userId);
    }

    /** 从 meta + 用户输入派生密钥 */
    private byte[] derive(VaultMeta meta, String masterKey) {
        return VaultCrypto.deriveKey(masterKey, meta.getSalt(), meta.getIterations());
    }

    /** 解锁校验：派生密钥解密 keyCheck 是否等于校验常量 */
    private boolean verifyMasterKey(String userId, VaultMeta meta, String masterKey) {
        try {
            byte[] key = derive(meta, masterKey);
            String plain = VaultCrypto.decrypt(key, meta.getKeyCheck());
            return VaultCrypto.KEY_CHECK_PLAIN.equals(plain);
        } catch (Exception e) {
            return false;
        }
    }

    private UnlockSession newSession(byte[] key) {
        UnlockSession s = new UnlockSession();
        s.key = key;
        s.lastAccess = System.currentTimeMillis();
        return s;
    }

    /** 已设置主密钥但未解锁 → 抛"密码库已锁定"；未设置主密钥返回配置密钥 */
    private byte[] currentKey(String userId) {
        VaultMeta meta = getMeta(userId);
        if (meta == null) {
            // 兼容旧数据：配置密钥（MD5 派生 32 字节）
            return SecureUtil.md5(cryptoKey).getBytes(StandardCharsets.UTF_8);
        }
        requireUnlocked(userId);
        UnlockSession session = sessions.get(userId);
        session.lastAccess = System.currentTimeMillis();
        return session.key;
    }

    private void requireUnlocked(String userId) {
        if (getMeta(userId) != null && !sessions.containsKey(userId)) {
            throw new BizException("密码库已锁定，请先输入主密钥解锁");
        }
    }

    private String encryptWith(byte[] key, String plain) {
        return VaultCrypto.encrypt(key, plain);
    }

    private String decryptWith(byte[] key, String cipherHex) {
        return VaultCrypto.decrypt(key, cipherHex);
    }

    /** 全量重加密：旧密钥（配置或旧主密钥）→ 新主密钥派生密钥 */
    private void reencryptAll(String userId, byte[] newKey) {
        List<Vault> all = list(new LambdaQueryWrapper<Vault>().eq(Vault::getUserId, userId));
        if (all.isEmpty()) return;
        byte[] oldKey = currentKey(userId);
        List<Vault> toUpdate = new ArrayList<>();
        for (Vault v : all) {
            if (v.getPassword() == null || v.getPassword().isBlank()) continue;
            try {
                String plain = decryptWith(oldKey, v.getPassword());
                if (plain == null) continue;
                v.setPassword(encryptWith(newKey, plain));
                v.setPasswordFingerprint(VaultCrypto.fingerprint(plain));
                toUpdate.add(v);
            } catch (Exception e) {
                log.warn("主密钥迁移跳过条目 id={}: {}", v.getId(), e.getMessage());
            }
        }
        if (!toUpdate.isEmpty()) updateBatchById(toUpdate);
    }

    // ============================================================
    // TOTP 双因素（种子用主密钥 AES 加密落 t_vault_totp，仅解锁态可用）
    // ============================================================

    /** 保存 / 更新 TOTP 种子（需已解锁）：body = { secret } */
    @Transactional
    public void saveTotp(Long vaultId, String secret) {
        requireUnlocked(StpUtil.getLoginIdAsString());
        Vault v = requireOwnedVault(vaultId);
        if (secret == null || secret.isBlank()) {
            throw new BizException("TOTP 种子不能为空");
        }
        byte[] key = currentKey(StpUtil.getLoginIdAsString());
        String cipher = VaultCrypto.encrypt(key, secret.trim());
        VaultTotp totp = vaultTotpMapper.selectById(vaultId);
        if (totp == null) {
            totp = new VaultTotp();
            totp.setVaultId(vaultId);
            totp.setSecret(cipher);
            vaultTotpMapper.insert(totp);
        } else {
            totp.setSecret(cipher);
            totp.setUpdateTime(LocalDateTime.now());
            vaultTotpMapper.updateById(totp);
        }
    }

    /** 读取并生成当前 TOTP 验证码（需已解锁）：{ totp, remaining } */
    public Map<String, Object> getTotp(Long vaultId) {
        String userId = StpUtil.getLoginIdAsString();
        requireUnlocked(userId);
        requireOwnedVault(vaultId);
        VaultTotp totp = vaultTotpMapper.selectById(vaultId);
        if (totp == null || totp.getSecret() == null || totp.getSecret().isBlank()) {
            throw new BizException("该条目未设置 TOTP");
        }
        byte[] key = currentKey(userId);
        String secret = VaultCrypto.decrypt(key, totp.getSecret());
        long now = System.currentTimeMillis();
        Map<String, Object> r = new HashMap<>();
        r.put("totp", generateTotp(secret, now / 1000));
        r.put("remaining", 30 - (now / 1000) % 30);
        return r;
    }

    /** 删除 TOTP（需已解锁） */
    @Transactional
    public void deleteTotp(Long vaultId) {
        requireUnlocked(StpUtil.getLoginIdAsString());
        requireOwnedVault(vaultId);
        vaultTotpMapper.deleteById(vaultId);
    }

    /** 校验条目归属当前用户 */
    private Vault requireOwnedVault(Long id) {
        Vault v = getById(id);
        String userId = StpUtil.getLoginIdAsString();
        if (v == null || !v.getUserId().equals(userId)) {
            throw new BizException("条目不存在");
        }
        return v;
    }

    /** RFC6238 TOTP：HMAC-SHA1，6 位，30s 步长（种子按标准 Base32 解码） */
    public static String generateTotp(String base32Secret, long timeSeconds) {
        long counter = timeSeconds / 30;
        byte[] key = base32Decode(base32Secret);
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new BizException("TOTP 计算失败");
        }
    }

    /** Base32 解码（RFC 4648，忽略空白与 = 填充） */
    private static byte[] base32Decode(String input) {
        final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String clean = input.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int bits = 0, value = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < clean.length(); i++) {
            value = (value << 5) | ALPHA.indexOf(clean.charAt(i));
            bits += 5;
            if (bits >= 8) {
                out.write((value >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    // ============================================================
    // 加密备份：导出（解密后按备份密码重加密）/ 导入（解密后按主密钥重加密入库）
    // 备份文件格式：base64(salt) + ":" + hex(AES-CBC(PBKDF2(备份密码,salt) 加密的 JSON))
    // ============================================================

    /**
     * 导出加密备份（需已解锁或未设置主密钥）：
     * 将全部条目（含回收站）解密为明文 → 组织 JSON → 用备份密码派生密钥加密
     *
     * @param backupPassword 备份密码（至少 8 位）
     * @return 加密备份字符串：base64(salt):hex(cipher)
     */
    public String exportBackup(String backupPassword) {
        if (backupPassword == null || backupPassword.length() < 8) {
            throw new BizException("备份密码至少 8 位");
        }
        cn.hutool.json.JSONObject payload = buildBackupPayload();
        try {
            String json = cn.hutool.json.JSONUtil.toJsonPrettyStr(payload);
            // 备份密钥派生
            String salt = VaultCrypto.generateSalt();
            byte[] backupKey = VaultCrypto.deriveKey(backupPassword, salt, VaultCrypto.PBKDF2_ITERATIONS);
            String cipher = VaultCrypto.encrypt(backupKey, json);
            return salt + ":" + cipher;
        } catch (Exception e) {
            throw new BizException("备份导出失败: " + e.getMessage());
        }
    }

    /** 构建密码库备份明文载荷（含解密后的密码明文，调用方负责加密），供单独备份与全量备份复用 */
    public cn.hutool.json.JSONObject buildBackupPayload() {
        String userId = StpUtil.getLoginIdAsString();
        requireUnlockedOrLegacy(userId);

        byte[] currentKey = currentKey(userId);
        List<Vault> all = list(new LambdaQueryWrapper<Vault>().eq(Vault::getUserId, userId));

        // 目录（type=5，密码库目录，含层级）
        List<Directory> dirs = directoryMapper.selectList(new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId)
                .eq(Directory::getType, 5));

        List<Map<String, Object>> dirList = new ArrayList<>();
        for (Directory d : dirs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("parentId", d.getParentId());
            m.put("name", d.getName());
            m.put("icon", d.getIcon());
            m.put("sortOrder", d.getSortOrder());
            dirList.add(m);
        }

        List<Map<String, Object>> entryList = new ArrayList<>();
        for (Vault v : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("directoryId", v.getDirectoryId());
            m.put("name", v.getName());
            m.put("username", v.getUsername());
            m.put("url", v.getUrl());
            m.put("notes", v.getNotes());
            m.put("sortOrder", v.getSortOrder());
            m.put("deleted", v.getDeleted());
            m.put("createTime", String.valueOf(v.getCreateTime()));
            // 解密密码明文（兼容损坏条目跳过）
            if (v.getPassword() != null && !v.getPassword().isBlank()) {
                try {
                    m.put("password", decryptWith(currentKey, v.getPassword()));
                } catch (Exception e) {
                    log.warn("备份导出条目密码解密失败 id={}: {}", v.getId(), e.getMessage());
                }
            }
            entryList.add(m);
        }

        cn.hutool.json.JSONObject payload = cn.hutool.json.JSONUtil.createObj();
        payload.set("app", "JNClub");
        payload.set("type", "vault-backup");
        payload.set("version", 1);
        payload.set("exportedAt", LocalDateTime.now().toString());
        payload.set("directories", dirList);
        payload.set("entries", entryList);
        return payload;
    }

    /**
     * 导入加密备份：解密 → 按 mode 合并或替换 → 重新按当前主密钥加密入库
     *
     * @param backupPassword 备份密码
     * @param backupContent  加密备份字符串（base64(salt):hex(cipher)）
     * @param mode           merge=合并（按 name+username 去重跳过）/ replace=先清空再导入
     * @return 导入统计 { imported, skipped }
     */
    @Transactional
    public Map<String, Object> importBackup(String backupPassword, String backupContent, String mode) {
        if (backupPassword == null || backupContent == null) {
            throw new BizException("缺少备份密码或备份内容");
        }
        // 解密
        String json;
        try {
            int idx = backupContent.indexOf(':');
            if (idx <= 0) throw new BizException("备份文件格式不正确");
            String salt = backupContent.substring(0, idx);
            String cipher = backupContent.substring(idx + 1);
            byte[] backupKey = VaultCrypto.deriveKey(backupPassword, salt, VaultCrypto.PBKDF2_ITERATIONS);
            json = VaultCrypto.decrypt(backupKey, cipher);
            if (json == null) throw new BizException("备份密码不正确或文件已损坏");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("备份解密失败: " + e.getMessage());
        }

        try {
            cn.hutool.json.JSONObject payload = cn.hutool.json.JSONUtil.parseObj(json);
            if (!"vault-backup".equals(payload.getStr("type"))) {
                throw new BizException("不是有效的密码库备份文件");
            }
            return restoreVaultPayload(payload, mode);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("备份导入失败: " + e.getMessage());
        }
    }

    /**
     * 从明文载荷恢复密码库（合并/替换），供备份导入与全量备份恢复复用。
     * 载荷结构：{ directories:[{id,parentId,name,icon,sortOrder}], entries:[{directoryId,name,username,password(明文),url,notes,sortOrder,deleted,createTime}] }
     *
     * @return 导入统计 { imported, skipped }
     */
    @Transactional
    public Map<String, Object> restoreVaultPayload(cn.hutool.json.JSONObject payload, String mode) {
        String userId = StpUtil.getLoginIdAsString();
        try {
            List<cn.hutool.json.JSONObject> dirs = payload.containsKey("directories")
                    ? payload.getJSONArray("directories").toList(cn.hutool.json.JSONObject.class)
                    : List.of();
            List<cn.hutool.json.JSONObject> entries = payload.containsKey("entries")
                    ? payload.getJSONArray("entries").toList(cn.hutool.json.JSONObject.class)
                    : List.of();

            if ("replace".equalsIgnoreCase(mode)) {
                // 清空现有：全部条目（含回收站）+ type=5 目录
                List<Vault> all = list(new LambdaQueryWrapper<Vault>().eq(Vault::getUserId, userId));
                if (!all.isEmpty()) {
                    removeBatchByIds(all.stream().map(Vault::getId).toList());
                }
                List<Directory> oldDirs = directoryMapper.selectList(new LambdaQueryWrapper<Directory>()
                        .eq(Directory::getUserId, userId).eq(Directory::getType, 5));
                for (Directory d : oldDirs) directoryMapper.deleteById(d.getId());
            }

            // 建目录：旧 id → 新 id 映射（先建一级，再建子级保证 parentId 有效）
            Map<String, Long> idMap = new HashMap<>();
            // 第一轮：parentId 为空或未在备份中的目录
            for (cn.hutool.json.JSONObject d : dirs) {
                Object pid = d.get("parentId");
                boolean parentInBackup = pid != null && dirs.stream().anyMatch(x -> String.valueOf(x.get("id")).equals(String.valueOf(pid)));
                if (pid == null || !parentInBackup) {
                    Long newId = createVaultDir(userId, null, d.getStr("name"), d.getStr("icon"), d.getInt("sortOrder", 0));
                    idMap.put(String.valueOf(d.get("id")), newId);
                }
            }
            // 第二轮：子目录
            for (cn.hutool.json.JSONObject d : dirs) {
                Object pid = d.get("parentId");
                boolean parentInBackup = pid != null && dirs.stream().anyMatch(x -> String.valueOf(x.get("id")).equals(String.valueOf(pid)));
                if (pid != null && parentInBackup) {
                    Long parentNewId = idMap.get(String.valueOf(pid));
                    Long newId = createVaultDir(userId, parentNewId, d.getStr("name"), d.getStr("icon"), d.getInt("sortOrder", 0));
                    idMap.put(String.valueOf(d.get("id")), newId);
                }
            }

            // 导入条目（需要主密钥：已设置主密钥需解锁，未设置用配置密钥）
            requireUnlockedOrLegacy(userId);
            byte[] key = currentKey(userId);
            int imported = 0, skipped = 0;
            for (cn.hutool.json.JSONObject e : entries) {
                String name = e.getStr("name");
                if (name == null || name.isBlank()) continue;
                Long directoryId = idMap.get(String.valueOf(e.get("directoryId")));
                if (directoryId == null) {
                    // 原目录不在备份中：跳过
                    skipped++;
                    continue;
                }
                if ("merge".equalsIgnoreCase(mode) && existsVault(userId, directoryId, name, e.getStr("username"))) {
                    skipped++;
                    continue;
                }
                Vault v = new Vault();
                v.setUserId(userId);
                v.setDirectoryId(directoryId);
                v.setName(name);
                v.setUsername(e.getStr("username", ""));
                v.setUrl(e.getStr("url", ""));
                v.setNotes(e.getStr("notes", ""));
                v.setSortOrder(e.getInt("sortOrder", 0));
                v.setDeleted(e.getInt("deleted", 0));
                String plainPwd = e.getStr("password");
                if (plainPwd != null && !plainPwd.isBlank()) {
                    v.setPassword(encryptWith(key, plainPwd));
                    v.setPasswordFingerprint(VaultCrypto.fingerprint(plainPwd));
                }
                save(v);
                imported++;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("imported", imported);
            result.put("skipped", skipped);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("备份导入失败: " + e.getMessage());
        }
    }

    /** 未设置主密钥（兼容旧数据）也可导出/导入；已设置需解锁 */
    private void requireUnlockedOrLegacy(String userId) {
        if (getMeta(userId) != null && !sessions.containsKey(userId)) {
            throw new BizException("密码库已锁定，请先输入主密钥解锁");
        }
    }

    private boolean existsVault(String userId, Long directoryId, String name, String username) {
        return count(new LambdaQueryWrapper<Vault>()
                .eq(Vault::getUserId, userId)
                .eq(Vault::getDirectoryId, directoryId)
                .eq(Vault::getName, name)
                .eq(Vault::getUsername, username == null ? "" : username)) > 0;
    }

    private Long createVaultDir(String userId, Long parentId, String name, String icon, int sortOrder) {
        if (name == null || name.isBlank()) name = "未命名";
        // merge 模式：同名同父目录已存在则复用（避免重复导入产生重复目录/条目）
        LambdaQueryWrapper<Directory> qw = new LambdaQueryWrapper<Directory>()
                .eq(Directory::getUserId, userId)
                .eq(Directory::getType, 5)
                .eq(Directory::getName, name);
        if (parentId == null) qw.isNull(Directory::getParentId);
        else qw.eq(Directory::getParentId, parentId);
        Directory existing = directoryMapper.selectOne(qw);
        if (existing != null) {
            return existing.getId();
        }
        Directory d = new Directory();
        d.setUserId(userId);
        d.setParentId(parentId);
        d.setName(name);
        d.setIcon(icon);
        d.setType(5);
        d.setSortOrder(sortOrder);
        directoryMapper.insert(d);
        return d.getId();
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private int intOr(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

}
