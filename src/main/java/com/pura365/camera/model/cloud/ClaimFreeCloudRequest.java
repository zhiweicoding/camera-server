package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 领取免费云存储请求
 */
@Data
@Schema(description = "领取免费7天云存储请求")
public class ClaimFreeCloudRequest {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVICE123456")
    private String deviceId;
}
