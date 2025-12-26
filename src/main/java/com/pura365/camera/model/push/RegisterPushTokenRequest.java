package com.pura365.camera.model.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 注册推送Token请求
 */
@Schema(description = "注册推送Token请求")
public class RegisterPushTokenRequest {

    /** 设备类型: iOS/Android */
    @Schema(description = "设备类型", example = "iOS", required = true)
    @JsonProperty("device_type")
    private String deviceType;

    /** 极光推送Registration ID */
    @Schema(description = "极光推送Registration ID", required = true)
    @JsonProperty("registration_id")
    private String registrationId;

    /** APP版本号 */
    @Schema(description = "APP版本号", example = "1.0.0")
    @JsonProperty("app_version")
    private String appVersion;

    /** 设备型号 */
    @Schema(description = "设备型号", example = "iPhone 14 Pro")
    @JsonProperty("device_model")
    private String deviceModel;

    /** 系统版本 */
    @Schema(description = "系统版本", example = "iOS 17.0")
    @JsonProperty("os_version")
    private String osVersion;

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }
}
