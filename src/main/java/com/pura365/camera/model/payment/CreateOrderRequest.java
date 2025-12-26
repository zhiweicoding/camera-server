package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.ToString;

/**
 * 创建支付订单请求
 */
@Schema(description = "创建支付订单请求")
@ToString
public class CreateOrderRequest {

    /**
     * 商品类型
     * 目前支持: cloud_storage (云存储套餐)
     */
    @Schema(description = "商品类型，目前支持: cloud_storage", example = "cloud_storage", required = true)
    @JsonProperty("product_type")
    private String productType;

    /**
     * 商品ID
     * 对于 cloud_storage 类型，这是云存储套餐的 plan_id
     */
    @Schema(description = "商品ID，云存储套餐的 plan_id", example = "plan_7day", required = true)
    @JsonProperty("product_id")
    private String productId;

    /**
     * 设备ID
     * 购买的套餐将绑定到该设备
     */
    @Schema(description = "设备ID，购买的套餐将绑定到该设备", required = true)
    @JsonProperty("device_id")
    private String deviceId;

    /**
     * 支付方式
     * 可选值: wechat, paypal, apple
     * 默认为 wechat
     */
    @Schema(description = "支付方式: wechat/paypal/apple", example = "wechat", defaultValue = "wechat")
    @JsonProperty("payment_method")
    private String paymentMethod;

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
