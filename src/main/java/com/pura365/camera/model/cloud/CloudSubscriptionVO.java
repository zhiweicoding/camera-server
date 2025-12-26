package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 云存储订阅状态信息
 */
@Data
@Schema(description = "云存储订阅状态信息")
public class CloudSubscriptionVO {

    /**
     * 是否已订阅
     */
    @JsonProperty("is_subscribed")
    @Schema(description = "是否已订阅", example = "true")
    private Boolean isSubscribed;

    /**
     * 套餐ID
     */
    @JsonProperty("plan_id")
    @Schema(description = "套餐ID", example = "motion-year-7d")
    private String planId;

    /**
     * 套餐名称
     */
    @JsonProperty("plan_name")
    @Schema(description = "套餐名称", example = "年卡【7天循环】")
    private String planName;

    /**
     * 到期时间
     */
    @JsonProperty("expire_at")
    @Schema(description = "到期时间（ISO8601格式）", example = "2024-12-13T00:00:00Z")
    private String expireAt;

    /**
     * 是否自动续费
     */
    @JsonProperty("auto_renew")
    @Schema(description = "是否自动续费", example = "false")
    private Boolean autoRenew;
}
