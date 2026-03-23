package com.pura365.camera.controller.app;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.TrafficPreviewPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * App-facing 4G package policy APIs.
 *
 * <p>Frontend integration guide:
 * <br/>1) Main control fields:
 * <br/>- can_preview: whether preview is allowed
 * <br/>- can_cloud_storage: whether cloud-storage actions are allowed
 * <br/>- can_use_package_feature: whether package feature is available
 * <br/>2) Device type:
 * <br/>- is_4g_device=true means apply 4G package policy
 * <br/>3) Suggested reason_code handling:
 * <br/>- TRIAL_FREE_PREVIEW: show "当前免费送7天体验功能"
 * <br/>- UNRECHARGED_TRIAL_EXPIRED: block preview/cloud and show recharge prompt
 * <br/>- SUBSCRIPTION_ACTIVE: normal available state
 * <br/>- TRAFFIC_NEAR_EXHAUSTION: available but show warning (>=80%)
 * <br/>- TRAFFIC_EXHAUSTED: block preview/cloud (>=100%)
 * <br/>4) Optional display fields:
 * <br/>- trial_days_left / usage_percent / has_active_traffic_plan / reason_message
 * </p>
 */
@Tag(name = "App4G流量策略", description = "给App提供4G套餐能力判断")
@RestController
@RequestMapping("/api/app/traffic")
public class AppTrafficController {

    @Autowired
    private TrafficPreviewPolicyService trafficPreviewPolicyService;

    /**
     * Frontend recommended flow:
     * <br/>- call this API before preview/cloud action
     * <br/>- use booleans first (can_preview/can_cloud_storage/can_use_package_feature)
     * <br/>- then render message by reason_code/reason_message
     */
    @Operation(
            summary = "查询设备4G套餐策略",
            description = "前端优先按 can_preview/can_cloud_storage/can_use_package_feature 控制交互，再根据 reason_code 做提示文案。"
    )
    @GetMapping({"/devices/{id}/package-policy", "/devices/{id}/preview-policy"})
    public ApiResponse<Map<String, Object>> getPackagePolicy(
            @RequestAttribute("currentUserId") Long currentUserId,
            @PathVariable("id") String deviceId) {

        TrafficPreviewPolicyService.PolicyEvaluation evaluation =
                trafficPreviewPolicyService.evaluate(currentUserId, deviceId);
        if (!evaluation.isOk()) {
            return ApiResponse.error(evaluation.getHttpStatus(), evaluation.getErrorMessage());
        }
        return ApiResponse.success(evaluation.getPolicy());
    }
}
