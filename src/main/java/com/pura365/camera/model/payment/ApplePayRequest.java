package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Apple Pay 支付请求
 */
@Schema(description = "Apple Pay 支付请求")
public class ApplePayRequest {

    /**
     * 订单ID
     */
    @Schema(description = "订单ID", required = true)
    @JsonProperty("order_id")
    private String orderId;

    /**
     * Apple IAP收据数据 (Base64编码)
     * 客户端从Apple IAP SDK获取的收据数据
     */
    @Schema(description = "Apple IAP收据数据(Base64编码)", required = true)
    @JsonProperty("receipt_data")
    private String receiptData;

    /**
     * Apple 交易ID
     */
    @Schema(description = "Apple交易ID", required = false)
    @JsonProperty("transaction_id")
    private String transactionId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getReceiptData() {
        return receiptData;
    }

    public void setReceiptData(String receiptData) {
        this.receiptData = receiptData;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
