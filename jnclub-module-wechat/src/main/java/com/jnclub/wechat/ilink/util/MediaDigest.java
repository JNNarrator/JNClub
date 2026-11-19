package com.jnclub.wechat.ilink.util;

import java.security.MessageDigest;

/**
 * 上传参数需要的摘要计算。
 *
 * <p>协议要求 {@code rawfilemd5} 是<b>明文</b>的 MD5（不是密文的），
 * 用它必须在加密之前算——这是一个容易写反的地方：
 * 先加密再算，服务端校验会失败但错误信息很含糊。
 */
public final class MediaDigest {

    private MediaDigest() {
    }

    public static String md5Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return AesEcb.toHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }
}
