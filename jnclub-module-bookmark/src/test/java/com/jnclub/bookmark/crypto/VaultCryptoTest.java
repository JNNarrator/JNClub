package com.jnclub.bookmark.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VaultCrypto 单元测试 — 密钥派生 / 加解密 / 校验密文 / 指纹 / 强度评分
 */
class VaultCryptoTest {

    @Test
    void deriveKeyIsDeterministicAnd256Bit() {
        String salt = VaultCrypto.generateSalt();
        byte[] k1 = VaultCrypto.deriveKey("master-pass-123", salt, VaultCrypto.PBKDF2_ITERATIONS);
        byte[] k2 = VaultCrypto.deriveKey("master-pass-123", salt, VaultCrypto.PBKDF2_ITERATIONS);
        assertArrayEquals(k1, k2, "同盐同主密钥派生结果必须一致");
        assertEquals(32, k1.length, "AES-256 需要 32 字节密钥");

        byte[] k3 = VaultCrypto.deriveKey("different-pass", salt, VaultCrypto.PBKDF2_ITERATIONS);
        assertFalse(java.util.Arrays.equals(k1, k3), "不同主密钥派生结果必须不同");
    }

    @Test
    void saltIsRandomPerGeneration() {
        assertNotEquals(VaultCrypto.generateSalt(), VaultCrypto.generateSalt());
    }

    @Test
    void encryptDecryptRoundTrip() {
        String salt = VaultCrypto.generateSalt();
        byte[] key = VaultCrypto.deriveKey("master-pass-123", salt, VaultCrypto.PBKDF2_ITERATIONS);
        String plain = "P@ssw0rd-2026!";
        String cipher = VaultCrypto.encrypt(key, plain);
        assertNotNull(cipher);
        assertNotEquals(plain, cipher, "密文不得等于明文");
        assertEquals(plain, VaultCrypto.decrypt(key, cipher), "解密必须还原明文");
    }

    @Test
    void nullOrBlankNotEncrypted() {
        String salt = VaultCrypto.generateSalt();
        byte[] key = VaultCrypto.deriveKey("k", salt, 1000);
        assertNull(VaultCrypto.encrypt(key, null));
        assertNull(VaultCrypto.encrypt(key, ""));
        assertNull(VaultCrypto.decrypt(key, null));
    }

    @Test
    void wrongKeyFailsToDecrypt() {
        String salt = VaultCrypto.generateSalt();
        byte[] k1 = VaultCrypto.deriveKey("right-key", salt, VaultCrypto.PBKDF2_ITERATIONS);
        byte[] k2 = VaultCrypto.deriveKey("wrong-key", salt, VaultCrypto.PBKDF2_ITERATIONS);
        String cipher = VaultCrypto.encrypt(k1, "secret");
        assertThrows(Exception.class, () -> VaultCrypto.decrypt(k2, cipher));
    }

    @Test
    void keyCheckRoundTrip() {
        // 模拟 setMasterKey 的校验密文：派生密钥加密校验常量 → 正确主密钥可解密匹配，错误主密钥不可
        String salt = VaultCrypto.generateSalt();
        String master = "my-master-key-2026";
        byte[] key = VaultCrypto.deriveKey(master, salt, VaultCrypto.PBKDF2_ITERATIONS);
        String keyCheck = VaultCrypto.encrypt(key, VaultCrypto.KEY_CHECK_PLAIN);

        assertEquals(VaultCrypto.KEY_CHECK_PLAIN, VaultCrypto.decrypt(key, keyCheck));

        byte[] wrongKey = VaultCrypto.deriveKey("wrong-master", salt, VaultCrypto.PBKDF2_ITERATIONS);
        assertThrows(Exception.class, () -> VaultCrypto.decrypt(wrongKey, keyCheck));
    }

    @Test
    void fingerprintDeterministicAndDifferent() {
        assertEquals(VaultCrypto.fingerprint("abc123"), VaultCrypto.fingerprint("abc123"));
        assertNotEquals(VaultCrypto.fingerprint("abc123"), VaultCrypto.fingerprint("abc124"));
        assertNull(VaultCrypto.fingerprint(""));
    }

    @Test
    void strengthScoreRanges() {
        assertEquals(0, VaultCrypto.strengthScore(""));
        // 强密码高分
        int strong = VaultCrypto.strengthScore("aB3!xY9@Qz7#Lm5*");
        assertTrue(strong >= 80, "强密码应 >=80，实际 " + strong);
        // 短密码低分
        int weak = VaultCrypto.strengthScore("123456");
        assertTrue(weak < 60, "常见弱密码应 <60，实际 " + weak);
        // 常见弱密码惩罚：即使较长也被扣分
        int common = VaultCrypto.strengthScore("password123");
        assertTrue(common < 80, "password123 应低于 80，实际 " + common);
        // 纯数字低分
        int digits = VaultCrypto.strengthScore("11111111");
        assertTrue(digits < 60, "纯数字应 <60，实际 " + digits);
    }

    @Test
    void strengthLevelMapping() {
        assertEquals("weak", VaultCrypto.strengthLevel(30));
        assertEquals("medium", VaultCrypto.strengthLevel(70));
        assertEquals("strong", VaultCrypto.strengthLevel(95));
    }
}
