package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.EnableStatus;
import lombok.Data;

import java.util.Date;

/**
 * 经销商实体，对应表 dealer
 * 原 Salesman(业务员) 重构为 Dealer(经销商)
 * 支持多级经销商层级结构
 */
@Data
@TableName("dealer")
public class Dealer {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 经销商代号 */
    @TableField("dealer_code")
    private String dealerCode;

    /** 经销商名称 */
    @TableField("name")
    private String name;

    /** 联系电话 */
    @TableField("phone")
    private String phone;

    /** 所属装机商ID */
    @TableField("installer_id")
    private Long installerId;

    /** 所属装机商代码 */
    @TableField("installer_code")
    private String installerCode;

    /** 上级经销商ID（NULL表示直属装机商） */
    @TableField("parent_dealer_id")
    private Long parentDealerId;

    /** 层级：1-一级经销商, 2-二级经销商... */
    @TableField("level")
    private Integer level;

    /** 状态: DISABLED-禁用, ENABLED-启用 */
    @TableField("status")
    private EnableStatus status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    // ==================== 企业信息字段 ====================

    /** 公司名称 */
    @TableField("company_name")
    private String companyName;

    /** 注册资本(万元) */
    @TableField("registered_capital")
    private String registeredCapital;

    /** 统一社会信用代码 */
    @TableField("credit_code")
    private String creditCode;

    /** 注册地址 */
    @TableField("registered_address")
    private String registeredAddress;

    /** 营业执照图片URL */
    @TableField("business_license")
    private String businessLicense;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
