package com.pura365.camera.model.wifi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备绑定状态响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备绑定状态")
public class BindingStatusVO {

    @JsonProperty("device_id")
    @Schema(description = "设备ID", example = "CAM202312001")
    private String deviceId;

    @JsonProperty("device_name")
    @Schema(description = "设备名称", example = "客厅摄像头")
    private String deviceName;

    @Schema(description = "绑定状态: binding-绑定中, success-成功, failed-失败", example = "binding")
    private String status;

    @Schema(description = "绑定进度(0-100)", example = "50")
    private Integer progress;

    @Schema(description = "状态消息", example = "正在配置WiFi")
    private String message;
}
