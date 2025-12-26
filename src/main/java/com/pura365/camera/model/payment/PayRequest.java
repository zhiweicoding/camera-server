package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通用支付请求（微信、PayPal）
 */
@Schema(description = "支付请求")
public class PayRequest {

    /**
     * 订单ID
     */
    @Schema(description = "订单ID", required = true)
    @JsonProperty("order_id")
    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
