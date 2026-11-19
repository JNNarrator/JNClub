package com.jnclub.wechat.ilink.media;

import com.jnclub.wechat.ilink.model.message.CDNMedia;
import com.jnclub.wechat.ilink.model.message.ImageItem;
import com.jnclub.wechat.ilink.model.message.MessageItem;
import com.jnclub.wechat.ilink.model.message.MessageType;
import com.jnclub.wechat.ilink.model.message.TextItem;
import com.jnclub.wechat.ilink.util.AesEcb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片消息项的结构与识别。
 */
class ImageItemTest {

    @Test
    @DisplayName("IMAGE 常量是 2——原 SDK 跳过的那个值")
    void imageTypeIsTwo() {
        assertEquals(2, MessageType.Item.IMAGE);
        assertEquals(1, MessageType.Item.TEXT);
        assertEquals(3, MessageType.Item.VOICE);
    }

    @Test
    @DisplayName("createImage 生成的结构能被 isImage 认出")
    void createImageIsRecognized() {
        ImageItem image = new ImageItem();
        image.setMedia(new CDNMedia("download-param", "key"));
        MessageItem item = MessageItem.createImage(image);

        assertTrue(MediaService.isImage(item));
        assertSame(image, item.getImageItem());
    }

    @Test
    @DisplayName("文本项不会被误判为图片")
    void textIsNotImage() {
        MessageItem item = new MessageItem();
        item.setType(MessageType.Item.TEXT);
        item.setTextItem(new TextItem("hello"));
        assertFalse(MediaService.isImage(item));
    }

    @Test
    @DisplayName("type 是 IMAGE 但 image_item 为空时不算图片——否则会空指针")
    void imageWithoutPayloadIsNotImage() {
        MessageItem item = new MessageItem();
        item.setType(MessageType.Item.IMAGE);
        item.setImageItem(null);
        assertFalse(MediaService.isImage(item));
    }

    @Test
    @DisplayName("aeskey(hex) 与 media.aes_key(base64) 编码的应是同一密钥")
    void twoKeyRepresentations() {
        byte[] key = AesEcb.randomKey();
        String hex = AesEcb.toHex(key);
        String asMediaKey = AesEcb.encodeAesKey(key);

        assertArrayEquals(AesEcb.fromHex(hex), key);
        assertArrayEquals(AesEcb.parseAesKey(asMediaKey), key);
    }

    @Test
    @DisplayName("图片加解密端到端：加密后的字节能被解密还原")
    void imageBytesSurviveEncryption() {
        byte[] key = AesEcb.randomKey();
        // 造一段看起来像 PNG 头的字节，验证二进制不会被文本编码破坏
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        byte[] cipher = AesEcb.encrypt(png, key);
        assertArrayEquals(png, AesEcb.decrypt(cipher, key));
        assertArrayEquals(
                "binary".getBytes(StandardCharsets.UTF_8),
                "binary".getBytes(StandardCharsets.UTF_8));
    }
}
