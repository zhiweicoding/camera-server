package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Apple Pay 支付结果响应
 */
@Schema(description = "Apple Pay 支付结果")
public class ApplePayVO {

    /**
     * Apple 交易ID
     */
    @Schema(description = "Apple 交易ID")
    @JsonProperty("transaction_id")
    private String transactionId;

    /**
     * 支付状态
     * completed: 支付成功
     */
    @Schema(description = "支付状态: completed")
    private String status;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
