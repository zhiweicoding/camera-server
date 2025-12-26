package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新分享权限请求
 */
@Data
@Schema(description = "更新分享权限请求")
public class SharePermissionUpdateRequest {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVICE123456")
    private String deviceId;

    /**
     * 目标用户ID
     */
    @JsonProperty("user_id")
    @Schema(description = "目标用户ID（被更新权限的用户）", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    private Long userId;

    /**
     * 新的权限
     */
    @JsonProperty("permission")
    @Schema(description = "新的权限: view_only-仅查看, full_control-完全控制", 
            requiredMode = Schema.RequiredMode.REQUIRED, example = "full_control")
    private String permission;
}
