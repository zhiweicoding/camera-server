package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 生成分享码请求
 */
@Data
@Schema(description = "生成设备分享码请求")
public class ShareGenerateRequest {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVICE123456")
    private String deviceId;

    /**
     * 分享权限
     */
    @JsonProperty("permission")
    @Schema(description = "分享权限: view_only-仅查看, full_control-完全控制", 
            example = "view_only", defaultValue = "view_only")
    private String permission;

    /**
     * 分享目标账号
     */
    @JsonProperty("target_account")
    @Schema(description = "分享目标账号（可为用户名/邮箱/手机号，不填则任何人可用）", example = "user@example.com")
    private String targetAccount;
}
