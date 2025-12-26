package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 云存储订阅实体，对应表 cloud_subscription
 */
@Data
@TableName("cloud_subscription")
public class CloudSubscription {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 套餐ID */
    @TableField("plan_id")
    private String planId;

    /** 套餐名称 */
    @TableField("plan_name")
    private String planName;

    /** 过期时间 */
    @TableField("expire_at")
    private Date expireAt;

    /** 是否自动续费: 0-否 1-是 */
    @TableField("auto_renew")
    private Integer autoRenew;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
