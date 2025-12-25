package com.pura365.camera.model.wifi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WiFi信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WiFi信息")
public class WifiInfoVO {

    @Schema(description = "WiFi SSID", example = "MyHome-5G")
    private String ssid;

    @Schema(description = "信号强度", example = "-50")
    private Integer signal;

    @Schema(description = "安全类型", example = "WPA2")
    private String security;

    @JsonProperty("is_connected")
    @Schema(description = "是否已连接", example = "true")
    private Boolean isConnected;
}
