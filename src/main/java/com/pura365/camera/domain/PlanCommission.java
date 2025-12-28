package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.CommissionFeeType;
import com.pura365.camera.enums.CommissionProfitMode;
import com.pura365.camera.enums.EnableStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 套餐分润配置实体，对应表 plan_commission
 * 存储套餐的手续费率、返点、成本、分润比例等财务配置
 */
@Data
@TableName("plan_commission")
public class PlanCommission {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 套餐ID（关联cloud_plan表的plan_id） */
    @TableField("plan_id")
    private String planId;

    /** 收款主体名称，如：AOCCX */
    @TableField("payee_entity")
    private String payeeEntity;

    /** 手续费类型: FIXED-固定比例, MIXED-混合（百分比+固定金额） */
    @TableField("fee_type")
    private CommissionFeeType feeType;

    /** 手续费比例（百分比，如30表示30%） */
    @TableField("fee_rate")
    private BigDecimal feeRate;

    /** 固定手续费金额（混合类型时使用） */
    @TableField("fee_fixed")
    private BigDecimal feeFixed;

    /** 套餐返点（百分比） */
    @TableField("rebate_rate")
    private BigDecimal rebateRate;

    /** 套餐成本 */
    @TableField("plan_cost")
    private BigDecimal planCost;

    /** 分润模式: PROFIT-按营收利润分润, REVENUE-按营收分润 */
    @TableField("profit_mode")
    private CommissionProfitMode profitMode;

    /** 装机商分润比例（百分比） */
    @TableField("installer_rate")
    private BigDecimal installerRate;

    /** 一级经销商分润比例（百分比） */
    @TableField("level1_rate")
    private BigDecimal level1Rate;

    /** 二级经销商分润比例（百分比） */
    @TableField("level2_rate")
    private BigDecimal level2Rate;

    /** 状态: ENABLED-启用, DISABLED-禁用 */
    @TableField("status")
    private EnableStatus status;

    /** 备注说明 */
    @TableField("remark")
    private String remark;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
