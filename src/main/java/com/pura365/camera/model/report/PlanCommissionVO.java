package com.pura365.camera.model.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 套餐分润配置VO（包含套餐基本信息）
 */
@Data
@Schema(description = "套餐分润配置详情")
public class PlanCommissionVO {

    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "套餐ID")
    private String planId;

    @Schema(description = "套餐名称")
    private String planName;

    @Schema(description = "套餐类型：motion-动态录像, fulltime-全天录像, traffic-4G流量")
    private String planType;

    @Schema(description = "套餐类型名称")
    private String planTypeName;

    @Schema(description = "套餐价格")
    private BigDecimal planPrice;

    @Schema(description = "收款主体名称")
    private String payeeEntity;

    @Schema(description = "手续费类型：fixed-固定比例，mixed-混合")
    private String feeType;

    @Schema(description = "手续费类型名称")
    private String feeTypeName;

    @Schema(description = "手续费比例（百分比）")
    private BigDecimal feeRate;

    @Schema(description = "固定手续费金额")
    private BigDecimal feeFixed;

    @Schema(description = "手续费描述（如：30% 或 4.4%+0.3USD）")
    private String feeDesc;

    @Schema(description = "套餐返点（百分比）")
    private BigDecimal rebateRate;

    @Schema(description = "套餐成本")
    private BigDecimal planCost;

    @Schema(description = "分润模式：profit-按营收利润分润，revenue-按营收分润")
    private String profitMode;

    @Schema(description = "分润模式名称")
    private String profitModeName;

    @Schema(description = "装机商分润比例（百分比）")
    private BigDecimal installerRate;

    @Schema(description = "一级经销商分润比例（百分比）")
    private BigDecimal level1Rate;

    @Schema(description = "二级经销商分润比例（百分比）")
    private BigDecimal level2Rate;

    @Schema(description = "状态: 1-启用, 0-禁用")
    private Integer status;

    @Schema(description = "状态名称")
    private String statusName;

    @Schema(description = "备注说明")
    private String remark;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "更新时间")
    private Date updatedAt;
}
