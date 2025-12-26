package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订阅云存储响应（支付订单信息）
 */
@Data
@Schema(description = "订阅云存储响应")
public class CloudSubscribeResponse {

    /**
     * 订单ID
     */
    @JsonProperty("order_id")
    @Schema(description = "订单ID", example = "order_1702483200000")
    private String orderId;

    /**
     * 支付金额
     */
    @JsonProperty("amount")
    @Schema(description = "支付金额（元）", example = "98.00")
    private BigDecimal amount;

    /**
     * 货币类型
     */
    @JsonProperty("currency")
    @Schema(description = "货币类型", example = "CNY")
    private String currency;

    /**
     * 支付方式
     */
    @JsonProperty("payment_method")
    @Schema(description = "支付方式", example = "wechat")
    private String paymentMethod;

    /**
     * 微信预支付ID（微信支付时返回）
     */
    @JsonProperty("prepay_id")
    @Schema(description = "微信预支付ID（微信支付时返回）", example = "mock_prepay_order_1702483200000")
    private String prepayId;

    /**
     * 签名（微信支付时返回）
     */
    @JsonProperty("sign")
    @Schema(description = "签名（微信支付时返回）", example = "mock_sign_order_1702483200000")
    private String sign;
}
