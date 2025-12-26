package com.pura365.camera.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 扫码绑定设备请求
 */
@Data
@Schema(description = "通过分享码绑定设备请求")
public class ShareBindRequest {

    /**
     * 分享码
     */
    @JsonProperty("share_code")
    @Schema(description = "分享码（可带前缀 PURA365_SHARE:）", 
            requiredMode = Schema.RequiredMode.REQUIRED, example = "PURA365_SHARE:ABC12345")
    private String shareCode;
}
