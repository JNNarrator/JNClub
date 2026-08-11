package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码库主密钥元数据（对应表 t_vault_meta）
 * <p>
 * 只存 KDF 参数与校验密文，密钥本身永不落库：
 * - salt + iterations + kdf：用户输入主密钥后按同样参数派生
 * - keyCheck：派生密钥加密的校验常量密文，解锁时解密校验输入是否正确
 */
@Data
@TableName("t_vault_meta")
public class VaultMeta {

    @TableId
    private String userId;

    /** 随机盐（Base64） */
    private String salt;

    /** PBKDF2 迭代次数 */
    private Integer iterations;

    /** 密钥版本（保留扩展位，暂恒为 1） */
    private Integer keyVersion;

    /** KDF 算法标识 */
    private String kdf;

    /** 校验密文：派生密钥加密 KEY_CHECK_PLAIN 的结果（Hex） */
    private String keyCheck;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
