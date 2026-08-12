package com.jnclub.sso;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Sa-Token 客户端会话持久化数据（对应表 sa_token_data）
 * <p>
 * 与 JN_SSO 服务端同款结构：value 为 AES-GCM 加密后的 Java 序列化值（base64），
 * 用以支撑 jn-token 会话跨后端重启存活，避免重启后需重新跳转 SSO。
 */
@Data
@TableName("sa_token_data")
public class SaTokenData {

    /** key（login:token: / login:session: 等前缀） */
    private String id;

    /** 加密后的序列化值（base64） */
    private String value;

    /** 数据类型：String / Object */
    @TableField("sa_type")
    private String saType;

    /** 过期毫秒时间戳，0 表示永不过期 */
    private Long expire;
}
