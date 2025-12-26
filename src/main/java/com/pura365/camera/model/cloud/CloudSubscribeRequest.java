package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订阅云存储请求
 */
@Data
@Schema(description = "订阅云存储套餐请求")
public class CloudSubscribeRequest {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVICE123456")
    private String deviceId;

    /**
     * 套餐ID
     */
    @JsonProperty("plan_id")
    @Schema(description = "套餐ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "motion-year-7d")
    private String planId;

    /**
     * 支付方式
     */
    @JsonProperty("payment_method")
    @Schema(description = "支付方式: wechat-微信支付, alipay-支付宝, apple-Apple Pay, google-Google Pay", 
            example = "wechat", defaultValue = "wechat")
    private String paymentMethod;
}
