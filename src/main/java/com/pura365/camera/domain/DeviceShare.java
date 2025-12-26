package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 设备分享码实体，对应表 device_share
 */
@Data
@TableName("device_share")
public class DeviceShare {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分享码（用于生成二维码） */
    @TableField("share_code")
    private String shareCode;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 分享者用户ID */
    @TableField("owner_user_id")
    private Long ownerUserId;

    /** 被分享者用户ID（扫码后填入） */
    @TableField("shared_user_id")
    private Long sharedUserId;

    /** 权限: view_only-仅查看, full_control-完全控制 */
    @TableField("permission")
    private String permission;

    /** 状态: pending-待使用, used-已使用, expired-已过期, revoked-已撤销 */
    @TableField("status")
    private String status;

    /** 过期时间 */
    @TableField("expire_at")
    private Date expireAt;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 使用时间 */
    @TableField("used_at")
    private Date usedAt;
}
