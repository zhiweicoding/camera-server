package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 订单信息响应
 */
@Schema(description = "订单信息")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderVO {

    /**
     * 订单ID
     */
    @Schema(description = "订单ID")
    @JsonProperty("order_id")
    private String orderId;

    /**
     * 订单状态
     * pending: 待支付, paid: 已支付, cancelled: 已取消, refunded: 已退款
     */
    @Schema(description = "订单状态: pending/paid/cancelled/refunded")
    private String status;

    /**
     * 订单金额
     */
    @Schema(description = "订单金额")
    private BigDecimal amount;

    /**
     * 货币类型
     */
    @Schema(description = "货币类型", example = "CNY")
    private String currency;

    /**
     * 创建时间 (ISO 8601 格式)
     */
    @Schema(description = "创建时间", example = "2024-01-01T12:00:00Z")
    @JsonProperty("created_at")
    private String createdAt;

    /**
     * 支付时间 (ISO 8601 格式)
     */
    @Schema(description = "支付时间")
    @JsonProperty("paid_at")
    private String paidAt;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(String paidAt) {
        this.paidAt = paidAt;
    }
}
