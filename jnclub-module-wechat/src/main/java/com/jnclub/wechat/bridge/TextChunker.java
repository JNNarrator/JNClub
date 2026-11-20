package com.jnclub.wechat.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * 长文本分片。
 *
 * <h2>为什么必须分片</h2>
 * iLink 单条消息的内容长度有上限，超长时服务端的行为并不可靠——
 * 可能直接失败，也可能静默截断。两种结果对用户都是"发出去的东西少了半截"
 * 却不知道为什么。剪贴板条目动辄几千字（一整段代码、一整篇文章），
 * 所以这是必然会踩到的问题，不是边界情况。
 *
 * <h2>为什么在换行处切</h2>
 * 按固定长度硬切会把一个词甚至一个字符切两半，读起来是断的。
 * 优先在最后一段宽度内的换行处断开，其次空格，都不行才硬切——
 * 这样常见情况下每片都是完整的行。
 *
 * <h2>分片不改变内容语义</h2>
 * 分片只影响"怎么发"，不影响"发什么"：拼起来必须与原文完全一致。
 * 因此这里不做任何 trim、不插入分隔符。
 */
public final class TextChunker {

    /**
     * 单片最大字符数。
     *
     * <p>取 1500 而不是 2048：微信侧对"字符"与"字节"的计数不完全一致，
     * 中文按 UTF-8 是 3 字节，若上限按字节算，1500 个中文字符会到 4.5KB。
     * 这里留出足够余量，避免中文长文在边界上被拒。
     */
    public static final int DEFAULT_CHUNK_CHARS = 1500;

    /** 单条消息最多拆成几片。再多就应该提示用户改用网页版，而不是刷屏。 */
    public static final int MAX_CHUNKS = 8;

    private TextChunker() {
    }

    /**
     * 把文本切成不超过 {@code maxChars} 的若干片。
     *
     * @return 分片列表；文本为空时返回只含原串的单元素列表
     */
    public static List<String> split(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            chunks.add(text == null ? "" : text);
            return chunks;
        }
        if (maxChars <= 0) {
            chunks.add(text);
            return chunks;
        }
        if (text.length() <= maxChars) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                // 在窗口内找一个"自然的断点"：优先换行，其次空格
                int newline = text.lastIndexOf('\n', end - 1);
                if (newline > start) {
                    end = newline + 1;
                } else {
                    int space = text.lastIndexOf(' ', end - 1);
                    // 不为了空格把片切得过短：至少保留窗口的一半
                    if (space > start + maxChars / 2) {
                        end = space + 1;
                    }
                }
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    /** 用默认上限分片。 */
    public static List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_CHARS);
    }

    /** 是否需要分片（供调用方决定是否加"分段"提示）。 */
    public static boolean needsSplit(String text) {
        return text != null && text.length() > DEFAULT_CHUNK_CHARS;
    }

    /** 按最多 MAX_CHUNKS 片估算：超出时建议用户改用网页版。 */
    public static boolean exceedsChunkBudget(String text) {
        return text != null && text.length() > (long) DEFAULT_CHUNK_CHARS * MAX_CHUNKS;
    }
}
