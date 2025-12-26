package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 扫码绑定设备响应
 */
@Data
@Schema(description = "扫码绑定设备响应")
public class ShareBindResponse {

    /**
     * 是否绑定成功
     */
    @JsonProperty("success")
    @Schema(description = "是否绑定成功", example = "true")
    private Boolean success;

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", example = "DEVICE123456")
    private String deviceId;

    /**
     * 设备名称
     */
    @JsonProperty("device_name")
    @Schema(description = "设备名称", example = "客厅摄像头")
    private String deviceName;

    /**
     * 分配的权限
     */
    @JsonProperty("permission")
    @Schema(description = "分配的权限", example = "view_only")
    private String permission;

    /**
     * 分享者用户ID
     */
    @JsonProperty("owner_id")
    @Schema(description = "分享者用户ID", example = "10001")
    private Long ownerId;
}
