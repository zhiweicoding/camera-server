package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * WiFi历史记录实体，对应表 wifi_history
 */
@Data
@TableName("wifi_history")
public class WifiHistory {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** WiFi名称 */
    @TableField("ssid")
    private String ssid;

    /** 信号强度 */
    @TableField("signal")
    private Integer signal;

    /** 安全类型 */
    @TableField("security")
    private String security;

    /** 是否连接: 0-否 1-是 */
    @TableField("is_connected")
    private Integer isConnected;

    /** 最后使用时间 */
    @TableField("last_used_at")
    private Date lastUsedAt;
}
