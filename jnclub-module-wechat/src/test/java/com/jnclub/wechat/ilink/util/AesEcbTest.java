package com.jnclub.wechat.ilink.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 媒体加密的单元测试。
 *
 * <p>这几条断言针对的都是"写错了也能跑、但结果是错的"的地方：
 * 填充长度算错会让服务端拒收，密钥解析认错一种编码会解出乱码，
 * 这两种故障都不会抛异常，只会表现为"图片发不过去"。
 */
class AesEcbTest {

    @Test
    @DisplayName("加密后能用同一密钥解回原文")
    void roundTrip() {
        byte[] key = AesEcb.randomKey();
        byte[] plain = "一张图片的字节内容".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = AesEcb.encrypt(plain, key);
        assertArrayEquals(plain, AesEcb.decrypt(cipher, key));
    }

    @Test
    @DisplayName("密钥长度固定为 16 字节")
    void keyLength() {
        assertEquals(16, AesEcb.randomKey().length);
    }

    @Test
    @DisplayName("填充长度符合协议的 ceil((n+1)/16)*16")
    void paddedSizeMatchesProtocol() {
        // 明文长度正好是块整数倍时，PKCS7 仍要补满一整块
        assertEquals(16, AesEcb.paddedSize(15));
        assertEquals(32, AesEcb.paddedSize(16));
        assertEquals(32, AesEcb.paddedSize(31));
        assertEquals(48, AesEcb.paddedSize(32));
        // 1000 字节补 8 字节到 1008；协议公式 ceil((1000+1)/16)*16 = 63*16 = 1008
        assertEquals(1008, AesEcb.paddedSize(1000));
    }

    @Test
    @DisplayName("实际密文长度与 paddedSize 一致——报给服务端的数字必须对得上")
    void cipherLengthMatchesReportedSize() {
        byte[] key = AesEcb.randomKey();
        for (int size : new int[]{1, 15, 16, 17, 1000, 4096}) {
            byte[] plain = new byte[size];
            byte[] cipher = AesEcb.encrypt(plain, key);
            assertEquals(AesEcb.paddedSize(size), cipher.length,
                    "明文 " + size + " 字节时密文长度不符");
        }
    }

    @Test
    @DisplayName("aes_key 能解析 base64(16字节) 形态")
    void parseRawKey() {
        byte[] key = AesEcb.randomKey();
        String encoded = Base64.getEncoder().encodeToString(key);
        assertArrayEquals(key, AesEcb.parseAesKey(encoded));
    }

    @Test
    @DisplayName("aes_key 能解析 base64(32字节hex字符串) 形态")
    void parseHexWrappedKey() {
        byte[] key = AesEcb.randomKey();
        String hex = AesEcb.toHex(key);
        String encoded = Base64.getEncoder().encodeToString(hex.getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(key, AesEcb.parseAesKey(encoded));
    }

    @Test
    @DisplayName("上传用的 encodeAesKey 能被 parseAesKey 解回同一密钥")
    void encodeThenParse() {
        byte[] key = AesEcb.randomKey();
        byte[] parsed = AesEcb.parseAesKey(AesEcb.encodeAesKey(key));
        assertArrayEquals(key, parsed);
    }

    @Test
    @DisplayName("长度不合法的 aes_key 明确抛错，而不是静默解出乱码")
    void rejectsBadKeyLength() {
        String bad = Base64.getEncoder().encodeToString(new byte[7]);
        assertThrows(IllegalArgumentException.class, () -> AesEcb.parseAesKey(bad));
        assertThrows(IllegalArgumentException.class, () -> AesEcb.parseAesKey(""));
    }

    @Test
    @DisplayName("hex 编解码可逆")
    void hexRoundTrip() {
        byte[] raw = AesEcb.randomKey();
        assertArrayEquals(raw, AesEcb.fromHex(AesEcb.toHex(raw)));
    }

    @Test
    @DisplayName("filekey 是 32 位小写 hex")
    void fileKeyFormat() {
        String key = AesEcb.randomFileKey();
        assertEquals(32, key.length());
        assertTrue(key.matches("[0-9a-f]{32}"));
    }
}
