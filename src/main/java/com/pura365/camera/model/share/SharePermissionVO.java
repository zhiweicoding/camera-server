package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备权限检查结果
 */
@Data
@Schema(description = "设备权限检查结果")
public class SharePermissionVO {

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(description = "设备ID", example = "DEVICE123456")
    private String deviceId;

    /**
     * 当前用户权限
     */
    @JsonProperty("permission")
    @Schema(description = "当前用户权限: owner-拥有者, view_only-仅查看, full_control-完全控制, null-无权限", 
            example = "owner")
    private String permission;

    /**
     * 是否可查看
     */
    @JsonProperty("can_view")
    @Schema(description = "是否可查看设备", example = "true")
    private Boolean canView;

    /**
     * 是否可控制
     */
    @JsonProperty("can_control")
    @Schema(description = "是否可控制设备（云台、对讲等）", example = "true")
    private Boolean canControl;

    /**
     * 是否是拥有者
     */
    @JsonProperty("is_owner")
    @Schema(description = "是否是设备拥有者", example = "true")
    private Boolean isOwner;
}
