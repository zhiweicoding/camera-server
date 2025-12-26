package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 取消分享请求
 */
@Data
@Schema(description = "取消设备分享请求")
public class ShareRevokeRequest {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVICE123456")
    private String deviceId;

    /**
     * 被取消分享的用户ID
     */
    @JsonProperty("user_id")
    @Schema(description = "被取消分享的用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    private Long userId;
}
