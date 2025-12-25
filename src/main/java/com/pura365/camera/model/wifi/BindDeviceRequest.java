package com.pura365.camera.model.wifi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 设备绑定请求
 */
@Data
@Schema(description = "设备绑定请求")
public class BindDeviceRequest {

    @NotBlank(message = "设备序列号不能为空")
    @JsonProperty("device_sn")
    @Schema(description = "设备序列号", example = "CAM202312001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceSn;

    @JsonProperty("device_name")
    @Schema(description = "设备名称", example = "客厅摄像头")
    private String deviceName;

    @JsonProperty("wifi_ssid")
    @Schema(description = "WiFi SSID", example = "MyHome-5G")
    private String wifiSsid;

    @JsonProperty("wifi_password")
    @Schema(description = "WiFi密码", example = "password123")
    private String wifiPassword;
}
