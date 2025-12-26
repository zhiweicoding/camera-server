package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 销售商实体，对应表 vendor
 */
@Data
@TableName("vendor")
public class Vendor {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销售商代码(2位) */
    @TableField("vendor_code")
    private String vendorCode;

    /** 销售商名称 */
    @TableField("vendor_name")
    private String vendorName;

    /** 联系人 */
    @TableField("contact_person")
    private String contactPerson;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 地址 */
    @TableField("address")
    private String address;

    /** 状态: 0-禁用 1-启用 */
    @TableField("status")
    private Integer status;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
