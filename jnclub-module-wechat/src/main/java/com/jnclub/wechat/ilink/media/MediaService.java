package com.jnclub.wechat.ilink.media;

import com.jnclub.wechat.ilink.api.IlinkApiClient;
import com.jnclub.wechat.ilink.exception.ApiException;
import com.jnclub.wechat.ilink.model.message.CDNMedia;
import com.jnclub.wechat.ilink.model.message.FileItem;
import com.jnclub.wechat.ilink.model.message.ImageItem;
import com.jnclub.wechat.ilink.model.message.VideoItem;
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
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MEDIA_TYPE_FILE = 3;

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
        // 复用统一的上传流程：先算明文摘要再加密，这个顺序只应存在一处。
        // 此前这里自己实现了一套，与 upload() 并存——将来只改一处就会
        // 出现"图片能传、文件不能传"这种不一致。
        UploadResult result = upload(toUserId, bytes, MEDIA_TYPE_IMAGE);
        ImageItem item = new ImageItem();
        item.setMedia(result.media());
        item.setMidSize(AesEcb.paddedSize(bytes.length));
        item.setAeskey(result.aesKeyHex());
        return item;
    }

    /**
     * 上传一个文件，返回可直接放进 {@code item_list} 的 {@link FileItem}。
     *
     * <p>{@code len} 必须是明文字节数的<b>字符串</b>（协议如此定义），
     * {@code md5} 也同样是明文的——两者都要在加密前算。
     */
    public FileItem uploadFile(String toUserId, String fileName, byte[] bytes) throws ApiException {
        UploadResult result = upload(toUserId, bytes, MEDIA_TYPE_FILE);
        FileItem item = new FileItem();
        item.setMedia(result.media());
        item.setFileName(fileName == null || fileName.isBlank() ? "unnamed" : fileName);
        item.setMd5(result.rawMd5());
        item.setLen(String.valueOf(bytes.length));
        return item;
    }

    /**
     * 上传一个视频，返回 {@link VideoItem}。
     *
     * <p>不生成封面：解码视频取帧的成本远高于收益，协议也不强制。
     */
    public VideoItem uploadVideo(String toUserId, byte[] bytes) throws ApiException {
        UploadResult result = upload(toUserId, bytes, MEDIA_TYPE_VIDEO);
        VideoItem item = new VideoItem();
        item.setMedia(result.media());
        item.setVideoSize((long) bytes.length);
        item.setVideoMd5(result.rawMd5());
        return item;
    }

    /** 下载文件字节。 */
    public byte[] downloadFile(FileItem item) throws ApiException {
        if (item == null || item.getMedia() == null) {
            throw new ApiException("文件项为空或缺少 media");
        }
        return download(item.getMedia(), null);
    }

    /** 下载视频字节。 */
    public byte[] downloadVideo(VideoItem item) throws ApiException {
        if (item == null || item.getMedia() == null) {
            throw new ApiException("视频项为空或缺少 media");
        }
        return download(item.getMedia(), null);
    }

    /**
     * 上传的公共结果。
     *
     * <p>额外带上原始的 AES key（hex）：图片项的 {@code aeskey} 字段要的就是
     * 这个值。之前尝试从 {@code media.aes_key} 反解回来——那条路径要猜编码
     * （base64 里装的是 hex 字符串还是裸字节），与其猜不如一开始就留着。
     */
    private record UploadResult(CDNMedia media, String rawMd5, String aesKeyHex) {
    }

    /**
     * 媒体上传的公共流程。
     *
     * <p>三种媒体只在 {@code media_type} 与"填哪些字段"上不同，
     * 加密、申请上传位、传 CDN 的步骤完全一致。抽出来是为了让
     * "先算明文摘要再加密"这个顺序只存在一处——这是最容易写反的地方。
     */
    private UploadResult upload(String toUserId, byte[] bytes, int mediaType) throws ApiException {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException("内容为空");
        }
        long rawSize = bytes.length;
        String rawMd5 = MediaDigest.md5Hex(bytes);

        String fileKey = AesEcb.randomFileKey();
        byte[] aesKey = AesEcb.randomKey();
        String aesKeyHex = AesEcb.toHex(aesKey);
        long cipherSize = AesEcb.paddedSize(bytes.length);

        GetUploadUrlRequest request = new GetUploadUrlRequest();
        request.setFilekey(fileKey);
        request.setMediaType(mediaType);
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

        byte[] cipher = AesEcb.encrypt(bytes, aesKey);
        String downloadParam = apiClient.uploadToCdn(
                CDN_BASE_URL, response.getUploadParam(), fileKey, cipher,
                Duration.ofMillis(SdkConfig.SEND_TIMEOUT_MS));
        logger.info("媒体已上传 CDN：type={} raw={}B cipher={}B", mediaType, rawSize, cipher.length);

        return new UploadResult(new CDNMedia(downloadParam, AesEcb.encodeAesKey(aesKey)), rawMd5, aesKeyHex);
    }

    /**
     * 从 CDN 取回并解密任意媒体的字节。
     *
     * @param inlineAesKey 消息项里单独携带的 hex 密钥（图片有这个字段），可为空
     */
    private byte[] download(CDNMedia media, String inlineAesKey) throws ApiException {
        if (media.getEncryptQueryParam() == null) {
            throw new ApiException("缺少下载参数");
        }
        byte[] aesKey;
        if (inlineAesKey != null && !inlineAesKey.isBlank()) {
            aesKey = AesEcb.fromHex(inlineAesKey);
        } else if (media.getAesKey() != null && !media.getAesKey().isBlank()) {
            aesKey = AesEcb.parseAesKey(media.getAesKey());
        } else {
            throw new ApiException("缺少 aes key");
        }
        byte[] cipher = apiClient.downloadFromCdn(
                CDN_BASE_URL, media.getEncryptQueryParam(), Duration.ofMillis(SdkConfig.SEND_TIMEOUT_MS));
        return AesEcb.decrypt(cipher, aesKey);
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

    /** 判断是不是文件消息项。 */
    public static boolean isFile(MessageItem item) {
        return item != null
                && item.getType() != null
                && item.getType() == MessageType.Item.FILE
                && item.getFileItem() != null;
    }

    /** 判断是不是视频消息项。 */
    public static boolean isVideo(MessageItem item) {
        return item != null
                && item.getType() != null
                && item.getType() == MessageType.Item.VIDEO
                && item.getVideoItem() != null;
    }

    /** 从消息项里判断它是不是图片。 */
    public static boolean isImage(MessageItem item) {
        return item != null
                && item.getType() != null
                && item.getType() == MessageType.Item.IMAGE
                && item.getImageItem() != null;
    }
}
