package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.EnableStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 装机商实体，对应表 installer
 * 原 Vendor(经销商) 重构为 Installer(装机商)
 */
@Data
@TableName("installer")
public class Installer {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 装机商代码(1-4位) */
    @TableField("installer_code")
    private String installerCode;

    /** 装机商名称 */
    @TableField("installer_name")
    private String installerName;

    /** 联系人 */
    @TableField("contact_person")
    private String contactPerson;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 地址 */
    @TableField("address")
    private String address;

    /** 分佣比例(基于可分润金额的百分比) */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 状态: DISABLED-禁用, ENABLED-启用 */
    @TableField("status")
    private EnableStatus status;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
