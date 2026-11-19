package com.jnclub.wechat.ilink.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * iLink CDN 媒体加解密：AES-128-ECB + PKCS7 填充，无 IV。
 *
 * <h2>为什么是 ECB 而不是 CBC/GCM</h2>
 * 这是微信侧协议的既定选择，不是我们可以挑的。ECB 在密码学上不是好模式
 * （相同明文块产生相同密文块，会泄漏结构），但这里的威胁模型是
 * "CDN 不应看到明文"，而不是"抵抗主动攻击者做流量分析"，
 * 密钥随请求走、每次上传都换，所以沿用协议即可。
 *
 * <h2>密文长度</h2>
 * 协议文档给的公式是 {@code ceil((plaintext + 1) / 16) * 16}，
 * 这其实就是 PKCS7 填充后的长度（PKCS7 在明文长度正好是 16 的倍数时
 * 仍要补满一整块，所以那个 +1 是必要的）。
 * 上传前必须用这个值报给 getUploadUrl，报错了服务端会拒收。
 */
public final class AesEcb {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int BLOCK = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesEcb() {
    }

    /** 生成一个随机 16 字节密钥。 */
    public static byte[] randomKey() {
        byte[] key = new byte[BLOCK];
        RANDOM.nextBytes(key);
        return key;
    }

    /** 生成 16 字节随机数的 hex 形式，用作 CDN 的 filekey。 */
    public static String randomFileKey() {
        byte[] raw = new byte[BLOCK];
        RANDOM.nextBytes(raw);
        return toHex(raw);
    }

    public static byte[] encrypt(byte[] plain, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM));
            return cipher.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalStateException("媒体加密失败: " + e.getMessage(), e);
        }
    }

    public static byte[] decrypt(byte[] cipherText, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("媒体解密失败: " + e.getMessage(), e);
        }
    }

    /** PKCS7 填充后的长度，即协议文档里的 {@code ceil((n+1)/16)*16}。 */
    public static int paddedSize(int plainSize) {
        return (plainSize / BLOCK + 1) * BLOCK;
    }

    /**
     * 解析 CDNMedia 里的 aes_key。
     *
     * <p>协议给了两种编码，必须都认：
     * <ul>
     *   <li>base64 解码后正好 16 字节 → 密钥本体</li>
     *   <li>base64 解码后是 32 字节的 hex 字符串 → 再 hex 解码成 16 字节</li>
     * </ul>
     * 只认其中一种会在遇到另一种时解密出一堆乱码，而且没有明确报错，
     * 很难排查，所以这里严格按长度判断。
     */
    public static byte[] parseAesKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("aes_key 为空");
        }
        byte[] decoded = java.util.Base64.getDecoder().decode(base64Key.trim());
        if (decoded.length == BLOCK) {
            return decoded;
        }
        if (decoded.length == BLOCK * 2) {
            String hex = new String(decoded, StandardCharsets.US_ASCII).trim();
            if (isHex(hex)) {
                return fromHex(hex);
            }
        }
        throw new IllegalArgumentException("无法识别的 aes_key 长度: " + decoded.length);
    }

    /** 上传时回给服务端的 aes_key：{@code base64(hex(key))}。 */
    public static String encodeAesKey(byte[] key) {
        return java.util.Base64.getEncoder().encodeToString(toHex(key).getBytes(StandardCharsets.US_ASCII));
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static boolean isHex(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
