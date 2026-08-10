package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码库实体（对应表 t_vault，目录复用 t_directory type=5）
 * 密码字段为 AES 密文存储，展示时经 VaultService 解密
 */
@Data
@TableName("t_vault")
public class Vault {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long directoryId;

    private String userId;

    /** 条目名称 */
    private String name;

    /** 账号 */
    private String username;

    /** 密码（AES 密文；列表查询不返回明文） */
    private String password;

    /** 站点地址 */
    private String url;

    /** 备注 */
    private String notes;

    /** 排序序号（同一目录内拖拽排序） */
    private Integer sortOrder;

    /** 软删除标记：0正常 1回收站 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
