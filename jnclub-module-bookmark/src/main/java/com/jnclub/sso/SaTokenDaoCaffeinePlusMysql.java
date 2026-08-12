package com.jnclub.sso;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.jnclub.sso.mapper.SaTokenDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Sa-Token Dao：Caffeine（读缓存）+ MySQL（持久化）。
 * <p>
 * 使 jn-token 会话及 Token-Session 跨后端重启存活，避免重启后需重新跳转 SSO。
 * value 落库前先 Java 序列化 + AES-GCM 加密，缓解 DB 泄露/注入导致的不安全反序列化。
 * 密钥由配置 {@code jnclub.sa-token-dao.crypto-key} 提供（优先环境变量 JNCLUB_DAO_CRYPTO_KEY）。
 * <p>
 * 实现对齐 JN_SSO 服务端 {@code SaTokenDaoCaffeinePlusMysql}，含 searchData 以支持
 * Sa-Token 的按账号查 token / 会话管理 / 踢人下线。
 */
@Component
public class SaTokenDaoCaffeinePlusMysql implements SaTokenDao {

    private static final Logger log = LoggerFactory.getLogger(SaTokenDaoCaffeinePlusMysql.class);
    private static final int GCM_IV_LEN = 12;

    private final Cache<String, Object> cache;
    private final SaTokenDataMapper mapper;
    private final javax.crypto.SecretKey cryptoKey;

    public SaTokenDaoCaffeinePlusMysql(Cache<String, Object> cache,
                                       SaTokenDataMapper mapper,
                                       @Value("${jnclub.sa-token-dao.crypto-key:jnclub-dev-crypto-key-change-me}") String cryptoKeyStr) {
        this.cache = cache;
        this.mapper = mapper;
        this.cryptoKey = deriveKey(cryptoKeyStr);
    }

    @Override
    public String get(String key) {
        Object val = cache.getIfPresent(key);
        if (val instanceof String s) return s;
        SaTokenData data = mapper.selectById(key);
        if (data != null) {
            if (data.getExpire() > 0 && Instant.now().toEpochMilli() > data.getExpire()) {
                mapper.deleteById(key);
                return null;
            }
            Object deserialized = deserializeValue(data.getValue());
            if (deserialized != null) {
                cache.put(key, deserialized);
            }
            if (deserialized instanceof String s) return s;
            return null;
        }
        return null;
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == 0 || timeout == SaTokenDao.NEVER_EXPIRE) {
            cache.put(key, value);
            saveToDb(key, value, "String", 0);
        } else {
            cache.put(key, value);
            saveToDb(key, value, "String", Instant.now().toEpochMilli() + timeout * 1000);
        }
    }

    @Override
    public void update(String key, String value) {
        SaTokenData existing = mapper.selectById(key);
        long expire = existing != null ? existing.getExpire() : 0;
        cache.put(key, value);
        saveToDb(key, value, "String", expire);
    }

    @Override
    public void delete(String key) {
        cache.invalidate(key);
        mapper.deleteById(key);
    }

    @Override
    public long getTimeout(String key) {
        SaTokenData data = mapper.selectById(key);
        if (data == null) return SaTokenDao.NOT_VALUE_EXPIRE;
        if (data.getExpire() == 0) return SaTokenDao.NEVER_EXPIRE;
        long remain = (data.getExpire() - Instant.now().toEpochMilli()) / 1000;
        return Math.max(remain, 0);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        SaTokenData data = mapper.selectById(key);
        if (data != null) {
            long expire = timeout == SaTokenDao.NEVER_EXPIRE ? 0 : Instant.now().toEpochMilli() + timeout * 1000;
            data.setExpire(expire);
            mapper.updateById(data);
        }
    }

    @Override
    public Object getObject(String key) {
        Object val = cache.getIfPresent(key);
        if (val != null) return val;
        SaTokenData data = mapper.selectById(key);
        if (data != null) {
            if (data.getExpire() > 0 && Instant.now().toEpochMilli() > data.getExpire()) {
                mapper.deleteById(key);
                return null;
            }
            Object deserialized = deserializeValue(data.getValue());
            if (deserialized != null) {
                cache.put(key, deserialized);
            }
            return deserialized;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> clazz) {
        Object val = getObject(key);
        if (val != null && clazz.isInstance(val)) {
            return clazz.cast(val);
        }
        return null;
    }

    @Override
    public void setObject(String key, Object value, long timeout) {
        if (timeout == 0 || timeout == SaTokenDao.NEVER_EXPIRE) {
            cache.put(key, value);
            saveToDb(key, value, "Object", 0);
        } else {
            cache.put(key, value);
            saveToDb(key, value, "Object", Instant.now().toEpochMilli() + timeout * 1000);
        }
    }

    @Override
    public void updateObject(String key, Object value) {
        SaTokenData existing = mapper.selectById(key);
        long expire = existing != null ? existing.getExpire() : 0;
        cache.put(key, value);
        saveToDb(key, value, "Object", expire);
    }

    @Override
    public void deleteObject(String key) {
        delete(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    @Override
    public SaSession getSession(String key) {
        Object val = getObject(key);
        if (val instanceof SaSession s) return s;
        return null;
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        setObject(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        updateObject(session.getId(), session);
    }

    @Override
    public void deleteSession(String key) {
        deleteObject(key);
    }

    @Override
    public long getSessionTimeout(String key) {
        return getObjectTimeout(key);
    }

    @Override
    public void updateSessionTimeout(String key, long timeout) {
        updateObjectTimeout(key, timeout);
    }

    @Override
    public List<String> searchData(String keyword, String type, int start, int size, boolean sortType) {
        LambdaQueryWrapper<SaTokenData> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SaTokenData::getId, keyword);
        }
        wrapper.orderByDesc(SaTokenData::getExpire);
        wrapper.last("LIMIT " + Math.max(start, 0) + ", " + Math.max(size, 1));
        return mapper.selectList(wrapper).stream()
                .map(SaTokenData::getId)
                .map(key -> keyword != null && key.startsWith(keyword) ? key.substring(keyword.length()) : key)
                .toList();
    }

    /**
     * 写入：先插入（主键冲突则改为更新），依赖 DuplicateKeyException 而非 ON DUPLICATE，兼容 MySQL 8。
     */
    private void saveToDb(String key, Object value, String saType, long expire) {
        SaTokenData data = new SaTokenData();
        data.setId(key);
        data.setValue(encryptValue(serializeValue(value)));
        data.setSaType(saType);
        data.setExpire(expire);
        try {
            mapper.insert(data);
        } catch (DuplicateKeyException e) {
            mapper.updateById(data);
        }
    }

    private String serializeValue(Object value) {
        byte[] bytes = SerializationUtils.serialize((Serializable) value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private Object deserializeValue(String stored) {
        String b64;
        try {
            b64 = decryptValue(stored);
        } catch (Exception e) {
            log.warn("[SaTokenDao] 读到未加密旧数据，按明文解析（将随过期自然淘汰）");
            b64 = stored;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            return SerializationUtils.deserialize(bytes);
        } catch (Exception e) {
            log.warn("[SaTokenDao] 数据解析失败，忽略该 key", e);
            return null;
        }
    }

    private String encryptValue(String plainBase64) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, cryptoKey, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plainBase64.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("SaTokenData 加密失败", e);
        }
    }

    private String decryptValue(String storedCipher) {
        try {
            byte[] all = Base64.getDecoder().decode(storedCipher);
            byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, GCM_IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, cryptoKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SaTokenData 解密失败（密钥是否与写入时一致？）", e);
        }
    }

    private static javax.crypto.SecretKey deriveKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法派生 SaTokenData 加密密钥", e);
        }
    }
}
