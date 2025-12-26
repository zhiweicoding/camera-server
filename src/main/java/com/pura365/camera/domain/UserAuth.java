package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 第三方登录绑定信息实体，对应表 user_auth
 */
@Data
@TableName("user_auth")
public class UserAuth {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 登录类型: wechat/apple/google/sms */
    @TableField("auth_type")
    private String authType;

    /** 第三方唯一用户标识，如 openid/sub */
    @TableField("open_id")
    private String openId;

    /** 微信UnionID */
    @TableField("union_id")
    private String unionId;

    /** 原始JSON信息 */
    @TableField("extra_info")
    private String extraInfo;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
