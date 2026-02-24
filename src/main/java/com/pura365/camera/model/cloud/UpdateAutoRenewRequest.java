package com.pura365.camera.model.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Update auto renew request.
 */
@Data
@Schema(description = "Update auto-renew request")
public class UpdateAutoRenewRequest {

    @JsonProperty("auto_renew")
    @Schema(description = "Whether auto renew is enabled", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean autoRenew;
}
