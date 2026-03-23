package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.EnableStatus;
import lombok.Data;

import java.util.Date;

/**
 * 用户推送Token实体，对应表 user_push_token
 */
@Data
@TableName("user_push_token")
public class UserPushToken {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备类型: iOS/Android */
    @TableField("device_type")
    private String deviceType;

    /** 推送Registration ID（JPush regId / FCM token / APNs device token） */
    @TableField("registration_id")
    private String registrationId;

    /** 推送提供方: jpush/fcm/apns */
    @TableField("provider")
    private String provider;

    /** 推送通道: jpush/fcm/apns（兼容字段） */
    @TableField("channel")
    private String channel;

    /** APP版本号 */
    @TableField("app_version")
    private String appVersion;

    /** 设备型号 */
    @TableField("device_model")
    private String deviceModel;

    /** 系统版本 */
    @TableField("os_version")
    private String osVersion;

    /** 是否启用: 0-禁用 1-启用 */
    @TableField("enabled")
    private EnableStatus enabled;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
