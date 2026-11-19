package com.jnclub.wechat.bridge;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 图片降采样与重编码，把微信原图压到剪贴板附件的 20MB 上限之内。
 *
 * <h2>为什么需要它</h2>
 * 手机拍的原图动辄 5–15MB，微信传的往往是原图；而 {@code ClipboardBlobService}
 * 的上限是 20MB（与桌面端 WebDAV 同步的图片阈值一致，不是随便定的）。
 * 直接拒绝会让"发张照片到电脑"这件最自然的事失败，所以宁可损失一些画质。
 *
 * <h2>策略：先降尺寸，再降质量</h2>
 * 只降质量对超大分辨率图收效有限（5000×4000 的图即使质量压到 0.5 仍可能很大），
 * 只降尺寸又会让文字截图糊掉。所以：
 * <ol>
 *   <li>长边超过 {@link #MAX_EDGE} 才等比缩小——截图通常远小于此，不受影响</li>
 *   <li>仍超限则逐步降低 JPEG 质量</li>
 *   <li>都不行才放弃，让调用方回一条明确的提示</li>
 * </ol>
 *
 * <p>注意：重编码会丢失 PNG 的透明通道，所以只有<b>确实超限</b>时才动它；
 * 没超限的图片原样通过，不碰。
 */
@Slf4j
public final class ImageCompressor {

    /** 长边上限。2400 足够看清照片内容，也基本能压进 20MB。 */
    private static final int MAX_EDGE = 2400;

    private ImageCompressor() {
    }

    /**
     * 把图片压到 {@code limitBytes} 以内。
     *
     * @return 压缩后的字节；若无需压缩则返回原字节；无法压缩返回 null
     */
    public static byte[] fitUnder(byte[] original, long limitBytes) {
        if (original == null || original.length <= limitBytes) {
            return original;
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(original));
        } catch (Exception e) {
            log.warn("图片读取失败，无法压缩：{}", e.getMessage());
            return null;
        }
        if (source == null) {
            // 不是 ImageIO 认识的格式（如 HEIC），我们既读不了也压不了。
            log.warn("不支持的图片格式，无法压缩");
            return null;
        }

        BufferedImage current = source;
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest > MAX_EDGE) {
            current = scale(source, MAX_EDGE);
        }

        // 先按 0.85 试；不够就逐级降质量，再不够就继续缩尺寸。
        float[] qualities = {0.85f, 0.7f, 0.5f, 0.35f};
        for (float quality : qualities) {
            byte[] encoded = encodeJpeg(current, quality);
            if (encoded != null && encoded.length <= limitBytes) {
                log.info("图片已压缩：{}B → {}B（q={}）", original.length, encoded.length, quality);
                return encoded;
            }
        }

        // 质量压到底仍超限，再缩一轮尺寸
        BufferedImage smaller = scale(current, (int) (MAX_EDGE * 0.6));
        byte[] encoded = encodeJpeg(smaller, 0.7f);
        if (encoded != null && encoded.length <= limitBytes) {
            log.info("图片已二次压缩：{}B → {}B", original.length, encoded.length);
            return encoded;
        }

        log.warn("图片压缩后仍超过 {}B，放弃", limitBytes);
        return null;
    }

    /** 等比缩放到长边为 {@code maxEdge}。 */
    private static BufferedImage scale(BufferedImage source, int maxEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return source;
        }
        double ratio = (double) maxEdge / longest;
        int targetW = Math.max(1, (int) Math.round(w * ratio));
        int targetH = Math.max(1, (int) Math.round(h * ratio));

        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // JPEG 没有透明通道，先铺白底，否则透明区域会变成黑色
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);
            g.drawImage(source, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) {
        try {
            // 透明图转 JPEG 前必须转成 RGB，否则 ImageIO 会抛异常
            BufferedImage rgb = image;
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, image.getWidth(), image.getHeight());
                    g.drawImage(image, 0, 0, null);
                } finally {
                    g.dispose();
                }
            }

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                return null;
            }
            ImageWriter writer = writers.next();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(stream);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("JPEG 编码失败：{}", e.getMessage());
            return null;
        }
    }
}
