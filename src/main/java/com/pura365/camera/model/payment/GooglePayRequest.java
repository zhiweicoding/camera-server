package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Google Play支付请求
 */
@Schema(description = "Google Play支付请求")
public class GooglePayRequest {

    /** 订单ID */
    @Schema(description = "订单ID", required = true)
    @JsonProperty("order_id")
    private String orderId;

    /** 购买Token(从Google Play Billing获取) */
    @Schema(description = "购买Token", required = true)
    @JsonProperty("purchase_token")
    private String purchaseToken;

    /** 产品ID */
    @Schema(description = "产品ID", required = true)
    @JsonProperty("product_id")
    private String productId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPurchaseToken() {
        return purchaseToken;
    }

    public void setPurchaseToken(String purchaseToken) {
        this.purchaseToken = purchaseToken;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
}
