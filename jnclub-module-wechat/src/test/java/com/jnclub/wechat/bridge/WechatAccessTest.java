package com.jnclub.wechat.bridge;

import com.jnclub.wechat.config.WechatUserConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 微信通道的准入控制（多租户）。
 *
 * <p>每条断言都对应一种"写宽松了就等于没有准入控制"的失误。
 * 微信 userId 形如 {@code o9cq80...@im.wechat}：一旦允许前缀匹配，
 * 填短一点的 id 就能放行一批陌生账号。
 */
class WechatAccessTest {

    private static WechatUserConfig withWhitelist(String raw) {
        WechatUserConfig config = new WechatUserConfig();
        config.setUserId("1");
        config.setWhitelist(raw);
        return config;
    }

    @Test
    @DisplayName("白名单为空表示不允许任何人——多租户下不能再空即放行")
    void emptyWhitelistRejectsAll() {
        // 单用户时代"空 = 不限制"是可以的；多租户下每个 bot 都是用户自己的号，
        // 空即放行意味着陌生人发消息就能写进主人的剪贴板。
        assertFalse(WechatMessageHandler.isAllowed(withWhitelist(""), "anyone@im.wechat"));
        assertFalse(WechatMessageHandler.isAllowed(withWhitelist(null), "anyone@im.wechat"));
    }

    @Test
    @DisplayName("精确匹配的 id 通过")
    void exactMatchAllowed() {
        WechatUserConfig c = withWhitelist("o9cq80zH_95q_2TZrVDCH9QisPU4@im.wechat");
        assertTrue(WechatMessageHandler.isAllowed(c, "o9cq80zH_95q_2TZrVDCH9QisPU4@im.wechat"));
    }

    @Test
    @DisplayName("前缀相同但更长的 id 必须被拒绝")
    void prefixExtensionRejected() {
        WechatUserConfig c = withWhitelist("o9cq80zH_95q_2TZrVDCH9QisPU4@im.wechat");
        // 把 startsWith 写回匹配逻辑时，这两种都会漏进来
        assertFalse(WechatMessageHandler.isAllowed(c, "o9cq80zH_95q_2TZrVDCH9QisPU4X@im.wechat"));
        assertFalse(WechatMessageHandler.isAllowed(c, "o9cq80zH_95q_2TZrVDCH9QisPU4@im.wechat.evil.com"));
    }

    @Test
    @DisplayName("只填一段前缀不应放行整个前缀下的账号")
    void barePrefixRejected() {
        WechatUserConfig c = withWhitelist("o9cq80");
        assertFalse(WechatMessageHandler.isAllowed(c, "o9cq80abc@im.wechat"));
        assertTrue(WechatMessageHandler.isAllowed(c, "o9cq80"));
    }

    @Test
    @DisplayName("空值与 null 一律拒绝，且不抛异常")
    void nullRejected() {
        WechatUserConfig c = withWhitelist("someone@im.wechat");
        assertFalse(WechatMessageHandler.isAllowed(c, null));
        assertFalse(WechatMessageHandler.isAllowed(c, ""));
        assertFalse(WechatMessageHandler.isAllowed(c, "   "));
    }

    @Test
    @DisplayName("大小写与首尾空白不影响判定")
    void normalizesWhitespaceAndCase() {
        WechatUserConfig c = withWhitelist("  O9CQ80ZH@IM.WECHAT  ");
        assertTrue(WechatMessageHandler.isAllowed(c, "o9cq80zh@im.wechat"));
    }

    @Test
    @DisplayName("多个白名单条目任一命中即通过")
    void multipleEntries() {
        WechatUserConfig c = withWhitelist("a@im.wechat, b@im.wechat");
        assertTrue(WechatMessageHandler.isAllowed(c, "b@im.wechat"));
        assertFalse(WechatMessageHandler.isAllowed(c, "c@im.wechat"));
    }
}
