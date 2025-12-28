package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pura365.camera.enums.PaymentOrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 支付订单实体，对应表 payment_order
 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    @TableField("order_id")
    private String orderId;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 设备ID */
    @TableField("device_id")
    private String deviceId;

    /** 经销商代码 */
    @TableField("vendor_code")
    private String vendorCode;

    /** 业务员ID */
    @TableField("salesman_id")
    private Long salesmanId;

    /** 业务员姓名(快照) */
    @TableField("salesman_name")
    private String salesmanName;

    /** 佣金比例(快照) */
    @TableField("commission_rate")
    private BigDecimal commissionRate;

    /** 业务员应得金额 */
    @TableField("salesman_amount")
    private BigDecimal salesmanAmount;

    /** 经销商应得金额 - 兼容旧数据 */
    @TableField("vendor_amount")
    private BigDecimal vendorAmount;

    /** 装机商ID */
    @TableField("installer_id")
    private Long installerId;

    /** 装机商代码 */
    @TableField("installer_code")
    private String installerCode;

    /** 装机商分润比例（快照） */
    @TableField("installer_rate")
    private BigDecimal installerRate;

    /** 装机商分润金额 */
    @TableField("installer_amount")
    private BigDecimal installerAmount;

    /** 经销商ID */
    @TableField("dealer_id")
    private Long dealerId;

    /** 经销商代号 */
    @TableField("dealer_code")
    private String dealerCode;

    /** 经销商分润比例（快照） */
    @TableField("dealer_rate")
    private BigDecimal dealerRate;

    /** 经销商分润金额 */
    @TableField("dealer_amount")
    private BigDecimal dealerAmount;

    /** 手续费金额 */
    @TableField("fee_amount")
    private BigDecimal feeAmount;

    /** 套餐成本 */
    @TableField("plan_cost")
    private BigDecimal planCost;

    /** 可分润金额 */
    @TableField("profit_amount")
    private BigDecimal profitAmount;

    /** 是否已结算：0-未结算 1-已结算 */
    @TableField("is_settled")
    private Integer isSettled;

    /** 设备上线所属国家 */
    @TableField("online_country")
    private String onlineCountry;

    /** 产品类型 */
    @TableField("product_type")
    private String productType;

    /** 产品ID */
    @TableField("product_id")
    private String productId;

    /** 订单金额 */
    @TableField("amount")
    private BigDecimal amount;

    /** 货币类型 */
    @TableField("currency")
    private String currency;

    /** 订单状态: pending-待支付, paid-已支付, cancelled-已取消, refunded-已退款 */
    @TableField("status")
    private PaymentOrderStatus status;

    /** 支付方式 */
    @TableField("payment_method")
    private String paymentMethod;

    /** 第三方订单ID */
    @TableField("third_order_id")
    private String thirdOrderId;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 支付时间 */
    @TableField("paid_at")
    private Date paidAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;

    /** 退款时间 */
    @TableField("refund_at")
    private Date refundAt;

    /** 退款原因 */
    @TableField("refund_reason")
    private String refundReason;
}
