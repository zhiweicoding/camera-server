package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 设备经销商归属实体，对应表 device_vendor
 * 记录设备在多级经销商体系中的归属关系
 * 支持查询设备的完整分销链路
 */
@Data
@TableName("device_vendor")
public class DeviceVendor {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 所属装机商ID */
    @TableField("installer_id")
    private Long installerId;

    /** 装机商代码 */
    @TableField("installer_code")
    private String installerCode;

    /** 当前归属经销商ID */
    @TableField("vendor_id")
    private Long vendorId;

    /** 当前归属经销商代码 */
    @TableField("vendor_code")
    private String vendorCode;

    /** 上级经销商ID（NULL表示直接从装机商获得） */
    @TableField("parent_vendor_id")
    private Long parentVendorId;

    /** 分润比例（基于上级利润的百分比） */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 经销商层级：1-一级经销商, 2-二级经销商... */
    @TableField("level")
    private Integer level;

    /** 来源转让记录ID（NULL表示初始分配） */
    @TableField("transfer_id")
    private Long transferId;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
