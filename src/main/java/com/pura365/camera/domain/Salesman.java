package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 业务员实体，对应表 salesman
 */
@Data
@TableName("salesman")
public class Salesman {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属经销商ID */
    @TableField("vendor_id")
    private Long vendorId;

    /** 所属经销商代码 */
    @TableField("vendor_code")
    private String vendorCode;

    /** 业务员姓名 */
    @TableField("name")
    private String name;

    /** 联系电话 */
    @TableField("phone")
    private String phone;

    /** 佣金比例(0-100) */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 状态: 0-禁用 1-启用 */
    @TableField("status")
    private Integer status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
