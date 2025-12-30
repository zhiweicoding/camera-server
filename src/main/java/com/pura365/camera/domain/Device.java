package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.enums.SdCardStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备实体，对应表 device
 */
@Data
@TableName("device")
public class Device {

    /** 设备ID(序列号) */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** MAC地址 */
    @TableField("mac")
    private String mac;

    /** WiFi SSID */
    @TableField("ssid")
    private String ssid;

    /** 地区 */
    @TableField("region")
    private String region;

    /** 设备名称 */
    @TableField("name")
    private String name;

    /** 固件版本 */
    @TableField("firmware_version")
    private String firmwareVersion;

    /** 状态: OFFLINE-离线, ONLINE-在线 */
    @TableField("status")
    private DeviceOnlineStatus status;

    /** 启用状态: DISABLED-禁用, ENABLED-启用 */
    @TableField("enabled")
    private EnableStatus enabled;

    /** 云存储开关 */
    @TableField("cloud_storage")
    private Integer cloudStorage;

    /** S3主机名 */
    @TableField("s3_hostname")
    private String s3Hostname;

    /** S3地区 */
    @TableField("s3_region")
    private String s3Region;

    /** S3访问密钥 */
    @TableField("s3_access_key")
    private String s3AccessKey;

    /** S3秘密密钥 */
    @TableField("s3_secret_key")
    private String s3SecretKey;

    /** MQTT主机名 */
    @TableField("mqtt_hostname")
    private String mqttHostname;

    /** MQTT用户名 */
    @TableField("mqtt_username")
    private String mqttUsername;

    /** MQTT密码 */
    @TableField("mqtt_password")
    private String mqttPassword;

    /** AI开关 */
    @TableField("ai_enabled")
    private Integer aiEnabled;

    /** GPT主机名 */
    @TableField("gpt_hostname")
    private String gptHostname;

    /** GPT密钥 */
    @TableField("gpt_key")
    private String gptKey;

    /** 最后在线时间 */
    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    // ==================== 摄像头实时状态字段 ====================

    /** 网络类型: 4G/wifi */
    @TableField("network_type")
    private String networkType;

    /** WiFi信号强度(RSSI) */
    @TableField("wifi_rssi")
    private Integer wifiRssi;

    /** TF卡状态: NOT_PRESENT-无, PRESENT-有 */
    @TableField("sd_state")
    private SdCardStatus sdState;

    /** TF卡总块数 */
    @TableField("sd_capacity")
    private Long sdCapacity;

    /** TF卡块大小(字节) */
    @TableField("sd_block_size")
    private Long sdBlockSize;

    /** TF卡空闲块数 */
    @TableField("sd_free")
    private Long sdFree;

    /** 画面旋转: 0-正常 1-旋转180度 */
    @TableField("rotate")
    private Integer rotate;

    /** 照明灯状态: 0-关闭 1-开启 */
    @TableField("light_led")
    private Integer lightLed;

    /** 白光灯状态: 0-禁用 1-启用 */
    @TableField("white_led")
    private Integer whiteLed;

    /** 最后心跳时间(用于离线检测) */
    @TableField("last_heartbeat_time")
    private LocalDateTime lastHeartbeatTime;

    /** 是否已领取7天免费云录像: 0-未领取 1-已领取 */
    @TableField("free_cloud_claimed")
    private Integer freeCloudClaimed;

    /** 上一次预览画面URL */
    @TableField("last_preview_url")
    private String lastPreviewUrl;

    // ==================== 灯泡配置字段 ====================

    /** 灯泡工作模式: 0-手动控制, 1-环境光自动控制, 2-定时控制 */
    @TableField("bulb_detect")
    private Integer bulbDetect;

    /** 灯泡亮度: 0-100 */
    @TableField("bulb_brightness")
    private Integer bulbBrightness;

    /** 灯泡开关状态: 0-关闭, 1-开启 */
    @TableField("bulb_enable")
    private Integer bulbEnable;

    /** 定时一: 开启时间 hh:mm */
    @TableField("bulb_time_on1")
    private String bulbTimeOn1;

    /** 定时一: 关闭时间 hh:mm */
    @TableField("bulb_time_off1")
    private String bulbTimeOff1;

    /** 定时二: 开启时间 hh:mm */
    @TableField("bulb_time_on2")
    private String bulbTimeOn2;

    /** 定时二: 关闭时间 hh:mm */
    @TableField("bulb_time_off2")
    private String bulbTimeOff2;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
