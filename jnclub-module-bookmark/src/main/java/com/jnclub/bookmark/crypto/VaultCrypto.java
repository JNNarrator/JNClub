package com.jnclub.bookmark.crypto;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 密码库主密钥加密工具 — PBKDF2 密钥派生 + AES 加解密 + 密码指纹与强度
 * <p>
 * 主密钥本身永不落库：库中仅存随机 salt 与派生密钥加密的校验密文（keyCheck），
 * 解锁时用用户输入派生密钥解密校验密文来确认正确性。
 * 加密算法复用现有 Hutool AES（AES/ECB/PKCS5Padding，32 字节密钥），
 * 与旧配置密钥加密的存量数据保持同一套加解密路径，迁移兼容。
 */
public final class VaultCrypto {

    /** 主密钥 PBKDF2 迭代次数（>=10 万） */
    public static final int PBKDF2_ITERATIONS = 100_000;
    public static final int SALT_BYTES = 16;
    /** keyCheck 校验常量：派生密钥需能解密出该明文 */
    public static final String KEY_CHECK_PLAIN = "jnclub-master-key-check-v1";

    private static final SecureRandom RANDOM = new SecureRandom();

    private VaultCrypto() {
    }

    /** 生成随机盐（Base64） */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * PBKDF2WithHmacSHA256 派生 32 字节 AES 密钥
     *
     * @param masterKey  用户主密钥
     * @param saltBase64 Base64 盐
     * @param iterations 迭代次数
     */
    public static byte[] deriveKey(String masterKey, String saltBase64, int iterations) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            PBEKeySpec spec = new PBEKeySpec(masterKey.toCharArray(), salt, iterations, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("主密钥派生失败", e);
        }
    }

    /** 用派生密钥加密（Hex），null/空返回 null */
    public static String encrypt(byte[] key, String plain) {
        if (plain == null || plain.isBlank()) return null;
        return aes(key).encryptHex(plain);
    }

    /** 用派生密钥解密（Hex → 明文），失败抛异常由调用方处理 */
    public static String decrypt(byte[] key, String cipherHex) {
        if (cipherHex == null || cipherHex.isBlank()) return null;
        return aes(key).decryptStr(cipherHex);
    }

    /** 派生固定 32 字节密钥的 Hutool AES */
    private static AES aes(byte[] key) {
        return SecureUtil.aes(key);
    }

    // ============================================================
    // 密码健康检查（只提示不拦截）
    // ============================================================

    /** SHA-256 指纹（Hex）：同用户重复密码检测用，不解密可比 */
    public static String fingerprint(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("指纹计算失败", e);
        }
    }

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    /** 常见弱密码（小写） */
    private static final String[] COMMON_WEAK = {
            "123456", "12345678", "123456789", "1234567890", "password", "password1",
            "1234567", "12345", "qwerty", "abc123", "111111", "000000", "iloveyou",
            "admin", "admin123", "test", "test123", "letmein", "welcome", "monkey",
    };

    /**
     * 密码强度评分 0-100
     * 长度 + 字符类别多样性 + 常见弱密码惩罚
     */
    public static int strengthScore(String plain) {
        if (plain == null || plain.isEmpty()) return 0;
        int len = plain.length();
        int score = 0;

        // 长度权重（满分 50）
        score += Math.min(50, len * 4);

        // 字符类别多样性（满分 30）
        int variety = 0;
        if (UPPER.matcher(plain).find()) variety++;
        if (LOWER.matcher(plain).find()) variety++;
        if (DIGIT.matcher(plain).find()) variety++;
        if (SYMBOL.matcher(plain).find()) variety++;
        score += variety * 8;

        // 常见弱密码惩罚（-30）
        if (len <= 12) {
            String lower = plain.toLowerCase();
            for (String weak : COMMON_WEAK) {
                if (lower.equals(weak)) {
                    score -= 30;
                    break;
                }
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    /** 强度分级：weak <60 / medium <80 / strong */
    public static String strengthLevel(int score) {
        if (score < 60) return "weak";
        if (score < 80) return "medium";
        return "strong";
    }
}
