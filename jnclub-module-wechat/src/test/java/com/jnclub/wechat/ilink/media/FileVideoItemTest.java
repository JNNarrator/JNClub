package com.jnclub.wechat.ilink.media;

import com.jnclub.wechat.ilink.model.message.CDNMedia;
import com.jnclub.wechat.ilink.model.message.FileItem;
import com.jnclub.wechat.ilink.model.message.MessageItem;
import com.jnclub.wechat.ilink.model.message.MessageType;
import com.jnclub.wechat.ilink.model.message.VideoItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件与视频消息项。
 */
class FileVideoItemTest {

    @Test
    @DisplayName("FILE=4、VIDEO=5——协议里的媒体类型编号")
    void typeConstants() {
        assertEquals(4, MessageType.Item.FILE);
        assertEquals(5, MessageType.Item.VIDEO);
        assertEquals(2, MessageType.Item.IMAGE);
    }

    @Test
    @DisplayName("createFile 生成的结构能被 isFile 认出")
    void createFileRecognized() {
        FileItem file = new FileItem();
        file.setMedia(new CDNMedia("param", "key"));
        file.setFileName("report.pdf");
        file.setMd5("abc");
        file.setLen("2048");

        MessageItem item = MessageItem.createFile(file);
        assertTrue(MediaService.isFile(item));
        assertFalse(MediaService.isVideo(item));
        assertFalse(MediaService.isImage(item));
        assertEquals("report.pdf", item.getFileItem().getFileName());
    }

    @Test
    @DisplayName("createVideo 生成的结构能被 isVideo 认出")
    void createVideoRecognized() {
        VideoItem video = new VideoItem();
        video.setMedia(new CDNMedia("param", "key"));
        video.setVideoSize(1024L);
        video.setVideoMd5("def");

        MessageItem item = MessageItem.createVideo(video);
        assertTrue(MediaService.isVideo(item));
        assertFalse(MediaService.isFile(item));
        assertEquals(1024L, item.getVideoItem().getVideoSize());
    }

    @Test
    @DisplayName("len 是字符串——协议如此，写成数字会反序列化失败")
    void lenIsString() {
        FileItem file = new FileItem();
        file.setLen("12345");
        assertEquals("12345", file.getLen());
    }

    @Test
    @DisplayName("类型对但载荷为空时不认作媒体，避免空指针")
    void emptyPayloadNotMedia() {
        MessageItem file = new MessageItem();
        file.setType(MessageType.Item.FILE);
        file.setFileItem(null);
        assertFalse(MediaService.isFile(file));

        MessageItem video = new MessageItem();
        video.setType(MessageType.Item.VIDEO);
        video.setVideoItem(null);
        assertFalse(MediaService.isVideo(video));
    }
}
