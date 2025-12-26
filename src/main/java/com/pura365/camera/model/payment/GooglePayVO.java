package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Google Play支付响应
 */
@Schema(description = "Google Play支付响应")
public class GooglePayVO {

    /** 订单ID */
    @Schema(description = "订单ID")
    @JsonProperty("order_id")
    private String orderId;

    /** 支付状态 */
    @Schema(description = "支付状态: completed/failed")
    @JsonProperty("status")
    private String status;

    /** Google订单ID */
    @Schema(description = "Google订单ID")
    @JsonProperty("google_order_id")
    private String googleOrderId;

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

    public String getGoogleOrderId() {
        return googleOrderId;
    }

    public void setGoogleOrderId(String googleOrderId) {
        this.googleOrderId = googleOrderId;
    }
}
