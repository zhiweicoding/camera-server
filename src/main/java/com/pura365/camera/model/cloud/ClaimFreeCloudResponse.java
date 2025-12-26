package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 领取免费云存储响应
 */
@Data
@Schema(description = "领取免费云存储响应")
public class ClaimFreeCloudResponse {

    /**
     * 是否领取成功
     */
    @JsonProperty("claimed")
    @Schema(description = "是否领取成功", example = "true")
    private Boolean claimed;

    /**
     * 到期时间
     */
    @JsonProperty("expire_at")
    @Schema(description = "到期时间（ISO8601格式）", example = "2023-12-20T00:00:00Z")
    private String expireAt;
}
