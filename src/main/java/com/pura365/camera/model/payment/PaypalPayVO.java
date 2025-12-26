package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PayPal 支付响应
 * 客户端使用 approval_url 跳转到 PayPal 完成支付
 */
@Schema(description = "PayPal 支付参数")
public class PaypalPayVO {

    /**
     * PayPal 支付页面 URL
     * 客户端需要跳转到该 URL 完成支付
     */
    @Schema(description = "PayPal 支付页面 URL")
    @JsonProperty("approval_url")
    private String approvalUrl;

    /**
     * PayPal 订单ID
     */
    @Schema(description = "PayPal 订单ID")
    @JsonProperty("paypal_order_id")
    private String paypalOrderId;

    public String getApprovalUrl() {
        return approvalUrl;
    }

    public void setApprovalUrl(String approvalUrl) {
        this.approvalUrl = approvalUrl;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }
}
