package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

/**
 * 添加设备请求参数
 *
 * @author camera-server
 */
@Schema(description = "添加设备请求")
@ToString
public class AddDeviceRequest {

    /**
     * 设备序列号（必填）
     */
    @NotBlank(message = "设备ID不能为空")
    @JsonProperty("device_id")
    @Schema(description = "设备序列号", required = true, example = "CAM20231201001")
    private String deviceId;

    /**
     * 设备名称（选填）
     */
    @JsonProperty("name")
    @Schema(description = "设备名称", example = "客厅摄像头")
    private String name;

    /**
     * WiFi SSID（选填）
     */
    @JsonProperty("wifi_ssid")
    @Schema(description = "WiFi名称", example = "HomeWiFi")
    private String wifiSsid;

    /**
     * WiFi 密码（选填）
     */
    @JsonProperty("wifi_password")
    @Schema(description = "WiFi密码")
    private String wifiPassword;

    /**
     * 设备MAC地址（选填）
     */
    @JsonProperty("mac")
    @Schema(description = "设备MAC地址", example = "AA:BB:CC:DD:EE:FF")
    private String mac;

    // Getters and Setters

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWifiSsid() {
        return wifiSsid;
    }

    public void setWifiSsid(String wifiSsid) {
        this.wifiSsid = wifiSsid;
    }

    public String getWifiPassword() {
        return wifiPassword;
    }

    public void setWifiPassword(String wifiPassword) {
        this.wifiPassword = wifiPassword;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }
}
