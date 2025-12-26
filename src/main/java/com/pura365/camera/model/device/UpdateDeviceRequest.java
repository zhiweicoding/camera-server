package com.pura365.camera.model.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新设备信息请求参数
 *
 * @author camera-server
 */
@Schema(description = "更新设备请求")
@Data
public class UpdateDeviceRequest {

    /**
     * 设备名称
     */
    @JsonProperty("name")
    @Schema(description = "设备名称", example = "卧室摄像头")
    private String name;

    /**
     * AI功能开关，0-关闭，1-开启
     */
    @JsonProperty("ai_enabled")
    @Schema(description = "AI功能开关，0-关闭，1-开启", example = "1")
    private Integer aiEnabled;

}
