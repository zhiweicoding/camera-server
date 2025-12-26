package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网络配置实体，对应表 network_config
 */
@Data
@TableName("network_config")
public class NetworkConfig {

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** WiFi名称 */
    @TableField("ssid")
    private String ssid;

    /** WiFi密码 */
    @TableField("password")
    private String password;

    /** 时区 */
    @TableField("timezone")
    private String timezone;

    /** 地区 */
    @TableField("region")
    private String region;

    /** IP地址 */
    @TableField("ip_address")
    private String ipAddress;

    /** 配网状态: 0-配网中 1-成功 2-失败 */
    @TableField("config_status")
    private Integer configStatus;

    /** 配网方式: qrcode/ble/audio */
    @TableField("config_method")
    private String configMethod;

    /** 配网来源 */
    @TableField("config_source")
    private String configSource;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
