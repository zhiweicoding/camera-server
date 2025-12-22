package com.pura365.camera.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 经销商应得金额 */
    @TableField("vendor_amount")
    private BigDecimal vendorAmount;

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

    /** 订单状态 */
    @TableField("status")
    private String status;

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
