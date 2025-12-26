package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分享用户信息
 */
@Data
@Schema(description = "分享用户信息")
public class ShareUserVO {

    /**
     * 用户ID
     */
    @JsonProperty("user_id")
    @Schema(description = "用户ID", example = "10002")
    private Long userId;

    /**
     * 用户名
     */
    @JsonProperty("username")
    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    /**
     * 昵称
     */
    @JsonProperty("nickname")
    @Schema(description = "昵称", example = "张三")
    private String nickname;

    /**
     * 头像URL
     */
    @JsonProperty("avatar")
    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    /**
     * 分享权限
     */
    @JsonProperty("permission")
    @Schema(description = "分享权限: view_only-仅查看, full_control-完全控制", example = "view_only")
    private String permission;

    /**
     * 分享时间
     */
    @JsonProperty("shared_at")
    @Schema(description = "分享时间（ISO8601格式）", example = "2023-12-13T12:00:00Z")
    private String sharedAt;
}
