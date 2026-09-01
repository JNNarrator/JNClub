package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebDAV 服务器实体（对应表 t_webdav_server，启动时幂等自建）
 * 个人 WebDAV 站点台账：URL + 独立账号密码（密码 AES 密文存储）+ 文件管理入口
 */
@Data
@TableName("t_webdav_server")
public class WebDavServer {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    private String userId;

    /** 站点名称（自定义别名） */
    private String name;

    /** WebDAV 服务地址（如 https://dav.example.com/） */
    private String url;

    /** 登录账号（可为空，如匿名 WebDAV） */
    private String username;

    /** 密码（AES 密文；列表/详情接口不返回明文） */
    private String password;

    /** 备注 */
    private String notes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
