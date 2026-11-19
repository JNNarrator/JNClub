package com.jnclub.wechat.bridge;

import java.util.List;

/**
 * 微信侧指令解析。
 *
 * <p>设计原则：<b>普通文本零仪式感</b>。用户直接发一句话就是"写入电脑剪贴板"，
 * 这是"顺手传一下"的核心体验，不能强迫记语法。只有以 {@code /} 开头的才算指令。
 *
 * @param kind   指令类型
 * @param arg    参数（数字或关键词），可能为空
 */
public record WechatCommand(Kind kind, String arg) {

    public enum Kind {
        /** 普通文本：写入剪贴板 */
        WRITE,
        /** /clip 或 /last：取最近一条 */
        CLIP,
        /** /clip N 或 /last N：取第 N 条 */
        CLIP_INDEX,
        /** /search 关键词 */
        SEARCH,
        /** /help */
        HELP
    }

    private static final int MAX_LIST = 5;

    public static WechatCommand parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new WechatCommand(Kind.WRITE, null);
        }
        String text = raw.trim();
        if (!text.startsWith("/")) {
            return new WechatCommand(Kind.WRITE, text);
        }

        String[] parts = text.split("\\s+", 2);
        String name = parts[0].substring(1).toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : null;

        return switch (name) {
            case "clip", "last" -> {
                if (arg == null || arg.isBlank()) {
                    yield new WechatCommand(Kind.CLIP, null);
                }
                // "/clip 3" 取第 3 条；非数字则当作搜索词
                if (arg.matches("\\d+")) {
                    int idx = Integer.parseInt(arg);
                    yield new WechatCommand(Kind.CLIP_INDEX, String.valueOf(Math.max(1, Math.min(idx, MAX_LIST))));
                }
                yield new WechatCommand(Kind.SEARCH, arg);
            }
            case "search", "s" -> new WechatCommand(Kind.SEARCH, arg);
            case "help", "?" -> new WechatCommand(Kind.HELP, null);
            default -> new WechatCommand(Kind.WRITE, text);
        };
    }

    public static String helpText(List<String> lines) {
        StringBuilder sb = new StringBuilder("📋 jn-clipboard 微信通道\n\n");
        sb.append("直接发任意文本 → 写入电脑剪贴板\n");
        sb.append("/clip → 取回最近一条\n");
        sb.append("/clip 3 → 取回第 3 条\n");
        sb.append("/search 关键词 → 搜索历史\n");
        sb.append("/help → 本说明");
        for (String line : lines) {
            sb.append("\n").append(line);
        }
        return sb.toString();
    }
}
