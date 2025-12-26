package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户Token信息实体，对应表 user_token
 */
@Data
@TableName("user_token")
public class UserToken {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 访问Token */
    @TableField("access_token")
    private String accessToken;

    /** 刷新Token */
    @TableField("refresh_token")
    private String refreshToken;

    /** 过期时间 */
    @TableField("expires_at")
    private Date expiresAt;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
