package com.pura365.camera.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 微信支付参数响应
 * 客户端使用这些参数调用微信支付 SDK
 */
@Schema(description = "微信支付参数")
public class WechatPayVO {

    /**
     * 微信开放平台 AppID
     */
    @Schema(description = "微信 AppID")
    private String appid;

    /**
     * 商户号
     */
    @Schema(description = "商户号")
    private String partnerid;

    /**
     * 预支付交易会话ID
     */
    @Schema(description = "预支付ID")
    private String prepayid;

    /**
     * 扩展字段
     * 固定值 "Sign=WXPay"
     */
    @Schema(description = "扩展字段", example = "Sign=WXPay")
    @JsonProperty("package")
    private String packageValue;

    /**
     * 随机字符串
     */
    @Schema(description = "随机字符串")
    private String noncestr;

    /**
     * 时间戳（秒）
     */
    @Schema(description = "时间戳（秒）")
    private String timestamp;

    /**
     * 签名
     */
    @Schema(description = "签名")
    private String sign;

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getPartnerid() {
        return partnerid;
    }

    public void setPartnerid(String partnerid) {
        this.partnerid = partnerid;
    }

    public String getPrepayid() {
        return prepayid;
    }

    public void setPrepayid(String prepayid) {
        this.prepayid = prepayid;
    }

    public String getPackageValue() {
        return packageValue;
    }

    public void setPackageValue(String packageValue) {
        this.packageValue = packageValue;
    }

    public String getNoncestr() {
        return noncestr;
    }

    public void setNoncestr(String noncestr) {
        this.noncestr = noncestr;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
}
