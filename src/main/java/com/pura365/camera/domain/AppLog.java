package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * App日志实体，对应表 app_log
 * 用于收集和排查客户端问题
 */
@Data
@TableName("app_log")
public class AppLog {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备ID (手机设备标识) */
    @TableField("device_id")
    private String deviceId;

    /** App版本 */
    @TableField("app_version")
    private String appVersion;

    /** 设备型号 (Android/iOS) */
    @TableField("device_model")
    private String deviceModel;

    /** 操作系统版本 */
    @TableField("os_version")
    private String osVersion;

    /** 日志级别 (debug/info/warning/error) */
    @TableField("level")
    private String level;

    /** 日志标签 (如: 配网, WebRTC等) */
    @TableField("tag")
    private String tag;

    /** 日志消息 */
    @TableField("message")
    private String message;

    /** 额外数据 (JSON格式) */
    @TableField("extra")
    private String extra;

    /** 客户端时间戳 */
    @TableField("client_time")
    private Date clientTime;

    /** 服务器接收时间 */
    @TableField("created_at")
    private Date createdAt;
}
