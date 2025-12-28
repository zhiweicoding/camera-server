package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * App用户实体，对应表 user
 */
@Data
@TableName("user")
public class User {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务用户ID，如 user_001 */
    @TableField("uid")
    private String uid;

    /** 登录账号（可为手机号/邮箱） */
    @TableField("username")
    private String username;

    /** 手机号 */
    @TableField("phone")
    private String phone;

    /** 邮箱 */
    @TableField("email")
    private String email;

    /** BCrypt密码哈希 */
    @TableField("password_hash")
    private String passwordHash;

    /** 角色: 1-流通用户 2-经销商 3-管理员 4-装机商 */
    @TableField("role")
    private Integer role;

    /** 关联装机商ID */
    @TableField("installer_id")
    private Long installerId;

    /** 关联经销商ID */
    @TableField("dealer_id")
    private Long dealerId;

    /** 是否为装机商身份: 0-否 1-是 */
    @TableField("is_installer")
    private Integer isInstaller;

    /** 是否为经销商身份: 0-否 1-是 */
    @TableField("is_dealer")
    private Integer isDealer;

    /** 昵称 */
    @TableField("nickname")
    private String nickname;

    /** 头像 */
    @TableField("avatar")
    private String avatar;

    /** 是否启用: 0-禁用 1-启用 */
    @TableField("enabled")
    private Integer enabled;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
