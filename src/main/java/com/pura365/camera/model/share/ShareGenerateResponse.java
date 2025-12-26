package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 生成分享码响应
 */
@Data
@Schema(description = "生成分享码响应")
public class ShareGenerateResponse {

    /**
     * 分享码
     */
    @JsonProperty("share_code")
    @Schema(description = "分享码", example = "ABC12345")
    private String shareCode;

    /**
     * 二维码内容（完整格式，用于生成二维码）
     */
    @JsonProperty("qr_content")
    @Schema(description = "二维码内容", example = "PURA365_SHARE:ABC12345")
    private String qrContent;

    /**
     * 过期时间
     */
    @JsonProperty("expire_at")
    @Schema(description = "过期时间（ISO8601格式）", example = "2023-12-14T00:00:00Z")
    private String expireAt;

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", example = "DEVICE123456")
    private String deviceId;

    /**
     * 分享权限
     */
    @JsonProperty("permission")
    @Schema(description = "分享权限", example = "view_only")
    private String permission;
}
