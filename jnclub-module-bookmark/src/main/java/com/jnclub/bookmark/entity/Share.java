package com.jnclub.bookmark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享实体（对应表 t_share，启动时幂等自建）
 * 公开只读链接：token 定位 + 可选密码 + 可选有效期
 */
@Data
@TableName("t_share")
public class Share {

    @TableId
    private String token;

    /** note / bookmark / file */
    private String refType;

    private Long refId;

    private String userId;

    /** 可选访问密码（bcrypt/salted-hash；为空则免密） */
    private String passwordHash;

    /** 过期时间；null=永不过期 */
    private LocalDateTime expiresAt;

    /** 访问统计(PV)：公开解析/下载成功解锁后自增 */
    private Integer visitCount;

    /** 最近访问时间 */
    private LocalDateTime lastVisitAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
