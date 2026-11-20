package com.jnclub.wechat.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 长文本分片。
 *
 * <p>核心不变式：<b>拼起来必须与原文完全一致</b>。分片只该影响"怎么发"，
 * 不该改变"发什么"——一旦这里丢了字符，用户看到的就是静默损坏的内容。
 */
class TextChunkerTest {

    @Test
    @DisplayName("短文本不分片，原样返回")
    void shortTextUntouched() {
        List<String> chunks = TextChunker.split("hello", 100);
        assertEquals(1, chunks.size());
        assertEquals("hello", chunks.get(0));
    }

    @Test
    @DisplayName("空文本返回单个空串，而不是空列表")
    void emptyText() {
        assertEquals(List.of(""), TextChunker.split("", 100));
        assertEquals(List.of(""), TextChunker.split(null, 100));
    }

    @Test
    @DisplayName("恰好等于上限时不切")
    void exactlyAtLimit() {
        String text = "a".repeat(100);
        assertEquals(1, TextChunker.split(text, 100).size());
    }

    @Test
    @DisplayName("超出上限时切分，且拼回等于原文")
    void splitsAndReassembles() {
        String text = "a".repeat(250);
        List<String> chunks = TextChunker.split(text, 100);
        assertTrue(chunks.size() >= 3);
        assertEquals(text, String.join("", chunks), "拼回必须与原文完全一致");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 100, "每片不应超过上限");
        }
    }

    @Test
    @DisplayName("优先在换行处切，不把一行劈成两半")
    void prefersNewlineBoundary() {
        // 每行 40 字符，上限 100：应该在行末断开，而不是第 100 个字符处
        String line = "x".repeat(39) + "\n";
        String text = line.repeat(5);
        List<String> chunks = TextChunker.split(text, 100);
        for (String chunk : chunks) {
            if (chunk.length() < text.length()) {
                assertTrue(chunk.endsWith("\n"), "非末片应在换行处结束: [" + chunk + "]");
            }
        }
        assertEquals(text, String.join("", chunks));
    }

    @Test
    @DisplayName("没有换行也没有空格时硬切，内容不丢")
    void hardCutsWithoutBoundaries() {
        String text = "中".repeat(500);
        List<String> chunks = TextChunker.split(text, 150);
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 150);
        }
    }

    @Test
    @DisplayName("中文按字符计数，不会因多字节而提前截断")
    void countsCharactersNotBytes() {
        String text = "中".repeat(1499);
        assertEquals(1, TextChunker.split(text, 1500).size());
    }

    @Test
    @DisplayName("needsSplit 只在上限之上为真")
    void needsSplitBoundary() {
        assertFalse(TextChunker.needsSplit("a".repeat(TextChunker.DEFAULT_CHUNK_CHARS)));
        assertTrue(TextChunker.needsSplit("a".repeat(TextChunker.DEFAULT_CHUNK_CHARS + 1)));
        assertFalse(TextChunker.needsSplit(null));
    }

    @Test
    @DisplayName("超出分片预算时给出信号，让调用方提示改用网页版")
    void exceedsBudget() {
        int budget = TextChunker.DEFAULT_CHUNK_CHARS * TextChunker.MAX_CHUNKS;
        assertFalse(TextChunker.exceedsChunkBudget("a".repeat(budget)));
        assertTrue(TextChunker.exceedsChunkBudget("a".repeat(budget + 1)));
    }

    @Test
    @DisplayName("分片数很多时内容依然完整")
    void manyChunksStillLossless() {
        String text = "abcde".repeat(1000);
        List<String> chunks = TextChunker.split(text, 200);
        assertEquals(text, String.join("", chunks));
    }
}
