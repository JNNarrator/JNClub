package com.jnclub.bookmark.service;

import com.jnclub.bookmark.entity.Bookmark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BookmarkService.sanitizeForInsert 字段长度兜底测试：
 * 超长 icon/title/url 分别对应列上限 2048/200/2048，验证归一化行为
 */
class BookmarkServiceSanitizeTest {

    @Test
    void 超长icon置空_避免DataTooLong() {
        Bookmark b = new Bookmark();
        b.setUrl("https://example.com");
        b.setIcon("data:image/png;base64," + "A".repeat(3000));
        BookmarkService.sanitizeForInsert(b);
        assertNull(b.getIcon());
    }

    @Test
    void 超长title截断到200() {
        Bookmark b = new Bookmark();
        b.setTitle("T".repeat(300));
        b.setUrl("https://example.com");
        BookmarkService.sanitizeForInsert(b);
        assertEquals(200, b.getTitle().length());
    }

    @Test
    void 超长url截断到2048() {
        Bookmark b = new Bookmark();
        b.setUrl("https://example.com/" + "x".repeat(3000));
        BookmarkService.sanitizeForInsert(b);
        assertEquals(2048, b.getUrl().length());
    }

    @Test
    void 正常字段不受影响() {
        Bookmark b = new Bookmark();
        b.setTitle("正常标题");
        b.setUrl("https://example.com");
        b.setIcon("https://example.com/favicon.ico");
        BookmarkService.sanitizeForInsert(b);
        assertEquals("正常标题", b.getTitle());
        assertEquals("https://example.com", b.getUrl());
        assertEquals("https://example.com/favicon.ico", b.getIcon());
    }
}
