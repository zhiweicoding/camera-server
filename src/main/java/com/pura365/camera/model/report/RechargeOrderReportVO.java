package com.pura365.camera.model.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 充值订单报表VO
 * 包含导出报表所需的所有字段
 */
@Data
@Schema(description = "充值订单报表")
public class RechargeOrderReportVO {

    // ============ 订单基本信息 ============

    @Schema(description = "充值订单号")
    private String orderId;

    @Schema(description = "下单时间")
    private Date createdAt;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "用户身份（如：装机商+经销商）")
    private String userRole;

    @Schema(description = "所属经销商名称")
    private String vendorName;

    @Schema(description = "经销商代码")
    private String vendorCode;

    // ============ 套餐信息 ============

    @Schema(description = "套餐ID")
    private String planId;

    @Schema(description = "套餐名称")
    private String planName;

    @Schema(description = "套餐类型：motion-动态录像, fulltime-全天录像, traffic-4G流量")
    private String planType;

    @Schema(description = "套餐类型名称")
    private String planTypeName;

    @Schema(description = "云存类型：云存/流量")
    private String cloudType;

    @Schema(description = "售价（套餐原价）")
    private BigDecimal originalPrice;

    // ============ 支付信息 ============

    @Schema(description = "支付金额")
    private BigDecimal payAmount;

    @Schema(description = "支付币种：CNY-人民币, USD-美元")
    private String currency;

    @Schema(description = "支付币种名称")
    private String currencyName;

    @Schema(description = "支付通道")
    private String paymentMethod;

    @Schema(description = "支付通道名称")
    private String paymentMethodName;

    @Schema(description = "支付订单号（内部）")
    private String payOrderId;

    @Schema(description = "支付时间")
    private Date paidAt;

    @Schema(description = "第三方订单号")
    private String thirdOrderId;

    // ============ 财务信息 ============

    @Schema(description = "收款主体")
    private String payeeEntity;

    @Schema(description = "手续费率描述（如：30% 或 4.4%+0.3USD）")
    private String feeRateDesc;

    @Schema(description = "手续费金额")
    private BigDecimal feeAmount;

    @Schema(description = "套餐返点描述")
    private String rebateDesc;

    @Schema(description = "套餐成本")
    private BigDecimal planCost;

    @Schema(description = "可分润金额")
    private BigDecimal profitAmount;

    // ============ 分润信息 ============

    @Schema(description = "分润模式")
    private String profitMode;

    @Schema(description = "分润模式名称")
    private String profitModeName;

    @Schema(description = "装机商分润比例")
    private String installerRateDesc;

    @Schema(description = "装机商分润金额")
    private BigDecimal installerAmount;

    @Schema(description = "一级分润比例")
    private String level1RateDesc;

    @Schema(description = "一级分润金额")
    private BigDecimal level1Amount;

    @Schema(description = "二级分润比例")
    private String level2RateDesc;

    @Schema(description = "二级分润金额")
    private BigDecimal level2Amount;

    // ============ 状态信息 ============

    @Schema(description = "订单状态：pending-待支付, paid-已支付, refunded-已退款")
    private String status;

    @Schema(description = "订单状态名称")
    private String statusName;
}
