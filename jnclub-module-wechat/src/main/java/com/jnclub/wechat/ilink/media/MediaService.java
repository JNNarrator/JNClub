package com.jnclub.wechat.ilink.media;

import com.jnclub.wechat.ilink.api.IlinkApiClient;
import com.jnclub.wechat.ilink.exception.ApiException;
import com.jnclub.wechat.ilink.model.message.CDNMedia;
import com.jnclub.wechat.ilink.model.message.ImageItem;
import com.jnclub.wechat.ilink.model.message.MessageItem;
import com.jnclub.wechat.ilink.model.message.MessageType;
import com.jnclub.wechat.ilink.model.request.BaseInfo;
import com.jnclub.wechat.ilink.model.request.GetUploadUrlRequest;
import com.jnclub.wechat.ilink.model.response.GetUploadUrlResponse;
import com.jnclub.wechat.ilink.util.AesEcb;
import com.jnclub.wechat.ilink.util.MediaDigest;
import com.jnclub.wechat.ilink.config.SdkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 媒体（图片）的收发：把"本地字节"与"CDN 引用"互相转换。
 *
 * <h2>上传为什么是这个顺序</h2>
 * <pre>
 * 1. 算明文的 size / md5      ← 必须在加密前
 * 2. 生成 filekey 与 aeskey
 * 3. 算密文长度（PKCS7 填充后）
 * 4. getUploadUrl 申请上传位   ← 服务端按第 1、3 步的数字校验
 * 5. 加密 → 上传 → 响应头拿到 downloadParam
 * 6. 构造 CDNMedia 放进 ImageItem
 * </pre>
 * 第 1 步和第 3 步的顺序写反是最常见的错误：先加密再算 md5，
 * 服务端校验会失败，但返回的错误信息很含糊，很难定位。
 */
public class MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);

    /** 与协议文档一致：1=IMAGE, 2=VIDEO, 3=FILE, 4=VOICE */
    public static final int MEDIA_TYPE_IMAGE = 1;

    static final String CDN_BASE_URL = "https://novac2c.cdn.weixin.qq.com/c2c";

    private final IlinkApiClient apiClient;

    public MediaService(IlinkApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 上传一张图片，返回可直接放进 {@code item_list} 的 {@link ImageItem}。
     */
    public ImageItem uploadImage(String toUserId, byte[] bytes) throws ApiException {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException("图片内容为空");
        }

        // 1. 明文的尺寸与摘要——必须在加密之前算
        long rawSize = bytes.length;
        String rawMd5 = MediaDigest.md5Hex(bytes);

        // 2. 两个密钥：filekey 标识这次上传，aeskey 保护内容
        String fileKey = AesEcb.randomFileKey();
        byte[] aesKey = AesEcb.randomKey();
        String aesKeyHex = AesEcb.toHex(aesKey);

        // 3. 密文长度：协议用 ceil((n+1)/16)*16，即 PKCS7 填充后的长度
        long cipherSize = AesEcb.paddedSize(bytes.length);

        // 4. 申请上传位
        GetUploadUrlRequest request = new GetUploadUrlRequest();
        request.setFilekey(fileKey);
        request.setMediaType(MEDIA_TYPE_IMAGE);
        request.setToUserId(toUserId);
        request.setRawsize(rawSize);
        request.setRawfilemd5(rawMd5);
        request.setFilesize(cipherSize);
        request.setNoNeedThumb(true);
        request.setAeskey(aesKeyHex);
        request.setBaseInfo(new BaseInfo());

        GetUploadUrlResponse response = apiClient.getUploadUrl(request, Duration.ofMillis(SdkConfig.SEND_TIMEOUT_MS));
        if (!response.isSuccess() || response.getUploadParam() == null) {
            throw new ApiException("申请上传位失败: " + response.getErrorInfo());
        }

        // 5. 加密并上传；下载参数在响应头 x-encrypted-param 里
        byte[] cipher = AesEcb.encrypt(bytes, aesKey);
        String downloadParam = apiClient.uploadToCdn(
                CDN_BASE_URL, response.getUploadParam(), fileKey, cipher,
                Duration.ofMillis(SdkConfig.SEND_TIMEOUT_MS));
        logger.info("图片已上传 CDN：raw={}B cipher={}B", rawSize, cipher.length);

        // 6. 构造引用。aes_key 的编码是 base64(hex(key))，
        //    与 parseAesKey 的第二种形态对应。
        CDNMedia media = new CDNMedia(downloadParam, AesEcb.encodeAesKey(aesKey));
        ImageItem item = new ImageItem();
        item.setMedia(media);
        item.setMidSize(cipher.length);
        item.setAeskey(aesKeyHex);
        return item;
    }

    /**
     * 从一条图片消息里取回原始字节。
     *
     * <p>密钥有两种来源：{@code image_item.aeskey}（hex 明文）优先，
     * 否则用 {@code media.aes_key}（base64 编码）。协议说前者优先，
     * 这里照做——两者可能同时存在，但只有一个是本次上传用的。
     */
    public byte[] downloadImage(ImageItem item) throws ApiException {
        if (item == null) {
            throw new ApiException("图片项为空");
        }
        CDNMedia media = item.getMedia();
        if (media == null || media.getEncryptQueryParam() == null) {
            throw new ApiException("图片缺少 media 或下载参数");
        }

        byte[] aesKey;
        if (item.getAeskey() != null && !item.getAeskey().isBlank()) {
            aesKey = AesEcb.fromHex(item.getAeskey());
        } else if (media.getAesKey() != null && !media.getAesKey().isBlank()) {
            aesKey = AesEcb.parseAesKey(media.getAesKey());
        } else {
            throw new ApiException("图片缺少 aes key");
        }

        byte[] cipher = apiClient.downloadFromCdn(
                CDN_BASE_URL, media.getEncryptQueryParam(), Duration.ofMillis(SdkConfig.SEND_TIMEOUT_MS));
        byte[] plain = AesEcb.decrypt(cipher, aesKey);
        logger.info("图片已从 CDN 下载：cipher={}B raw={}B", cipher.length, plain.length);
        return plain;
    }

    /** 从消息项里判断它是不是图片。 */
    public static boolean isImage(MessageItem item) {
        return item != null
                && item.getType() != null
                && item.getType() == MessageType.Item.IMAGE
                && item.getImageItem() != null;
    }
}
