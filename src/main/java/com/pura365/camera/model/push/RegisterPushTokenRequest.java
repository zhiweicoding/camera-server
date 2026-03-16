package com.pura365.camera.model.push;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"deviceType"})
    private String deviceType;

    /** 推送Registration ID（JPush regId 或 FCM token） */
    @Schema(description = "推送Registration ID", required = true)
    @JsonProperty("registration_id")
    @JsonAlias({"registrationId"})
    private String registrationId;

    /** 推送提供方: jpush/fcm */
    @Schema(description = "推送提供方", example = "jpush")
    @JsonProperty("provider")
    @JsonAlias({"push_provider", "pushProvider"})
    private String provider;

    /** 推送通道: jpush/fcm（兼容字段） */
    @Schema(description = "推送通道", example = "jpush")
    @JsonProperty("channel")
    @JsonAlias({"push_channel", "pushChannel"})
    private String channel;

    /** APP版本号 */
    @Schema(description = "APP版本号", example = "1.0.0")
    @JsonProperty("app_version")
    @JsonAlias({"appVersion"})
    private String appVersion;

    /** 设备型号 */
    @Schema(description = "设备型号", example = "iPhone 14 Pro")
    @JsonProperty("device_model")
    @JsonAlias({"deviceModel"})
    private String deviceModel;

    /** 系统版本 */
    @Schema(description = "系统版本", example = "iOS 17.0")
    @JsonProperty("os_version")
    @JsonAlias({"osVersion"})
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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
