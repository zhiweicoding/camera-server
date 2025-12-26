package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户设备关联实体，对应表 user_device
 */
@Data
@TableName("user_device")
public class UserDevice {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 角色: owner/viewer */
    @TableField("role")
    private String role;

    /** 权限: view_only-仅查看, full_control-完全控制 */
    @TableField("permission")
    private String permission;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;
}
