package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 设备绑定实体，对应表 device_binding
 */
@Data
@TableName("device_binding")
public class DeviceBinding {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 设备序列号 */
    @TableField("device_sn")
    private String deviceSn;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** WiFi名称 */
    @TableField("wifi_ssid")
    private String wifiSsid;

    /** WiFi密码 */
    @TableField("wifi_password")
    private String wifiPassword;

    /** 绑定状态 */
    @TableField("status")
    private String status;

    /** 绑定进度 */
    @TableField("progress")
    private Integer progress;

    /** 状态消息 */
    @TableField("message")
    private String message;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
