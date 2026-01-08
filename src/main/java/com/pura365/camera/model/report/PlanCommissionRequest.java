package com.pura365.camera.model.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 套餐分润配置请求
 */
@Data
@Schema(description = "套餐分润配置请求")
public class PlanCommissionRequest {

    @Schema(description = "套餐ID", required = true)
    @NotBlank(message = "套餐ID不能为空")
    private String planId;

    @Schema(description = "收款主体名称，如：AOCCX")
    private String payeeEntity;

    @Schema(description = "手续费类型：fixed-固定比例，mixed-混合（百分比+固定金额）", example = "fixed")
    private String feeType;

    @Schema(description = "手续费比例（百分比，如30表示30%）", example = "30")
    private BigDecimal feeRate;

    @Schema(description = "固定手续费金额（混合类型时使用）", example = "0.3")
    private BigDecimal feeFixed;

    @Schema(description = "套餐返点（百分比）")
    private BigDecimal rebateRate;

    @Schema(description = "套餐成本")
    private BigDecimal planCost;

    @Schema(description = "分润模式：profit-按营收利润分润，revenue-按营收分润", example = "profit")
    private String profitMode;

    @Schema(description = "状态: 1-启用, 0-禁用", example = "1")
    private Integer status;

    @Schema(description = "备注说明")
    private String remark;
}
