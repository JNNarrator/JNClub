package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码库 TOTP 双因素种子（对应表 t_vault_totp，启动时幂等自建）
 * 种子用密码库主密钥 AES 加密后落库，仅解锁态可读写
 */
@Data
@TableName("t_vault_totp")
public class VaultTotp {

    @TableId
    private Long vaultId;

    /** 主密钥 AES 加密后的种子密文（Hex） */
    private String secret;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
