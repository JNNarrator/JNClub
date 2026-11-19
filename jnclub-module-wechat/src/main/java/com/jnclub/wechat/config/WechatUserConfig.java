package com.jnclub.wechat.config;

import lombok.Data;

/**
 * 单个用户的微信通道配置。
 *
 * <p>多租户下每个 JNClub 账号有自己的通道、自己的白名单、自己的开关。
 * 存在 {@code t_wechat_config} 里，{@code user_id} 是主键。
 */
@Data
public class WechatUserConfig {

    private String userId;

    /** 通道总开关；关闭时不自动恢复监听。 */
    private boolean enabled = true;

    /**
     * 允许写入剪贴板的微信 userId，逗号分隔。
     *
     * <p>为空时的语义是<b>不允许任何人</b>——多租户下不能再默认放行：
     * 每个 bot 都是用户自己的微信号，别人给它发消息会把内容写进主人的空间。
     * 用户扫码登录后会自动把本人加入白名单，所以正常情况下不会为空。
     */
    private String whitelist = "";

    /** 电脑复制后是否推送到微信。 */
    private boolean notifyOnCopy = true;
}
