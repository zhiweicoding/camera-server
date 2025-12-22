package com.pura365.camera.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 微信登录请求
 */
@Schema(description = "微信登录请求")
public class WechatLoginRequest {

    /**
     * 微信授权码
     * 客户端通过微信 SDK 获取
     */
    @Schema(description = "微信授权码", required = true, example = "0x1a2b3c4d5e6f")
    @JsonProperty("code")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
