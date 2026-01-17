package com.pura365.camera.controller.app;

import com.pura365.camera.domain.ManufacturedDevice;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.DeviceProductionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * App 通用接口
 * 包含:
 * - 版本检查
 * - 提交反馈
 */
@Tag(name = "App通用接口", description = "App版本检查、反馈等通用接口")
@RestController
@RequestMapping("/api/app")
public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    @Autowired
    private DeviceProductionService deviceProductionService;
    /**
     * 版本检查
     * GET /api/app/version?platform=ios&current_version=1.0.0
     */
    @Operation(summary = "版本检查", description = "检查App是否需要更新")
    @GetMapping("/version")
    public ApiResponse<Map<String, Object>> checkVersion(
            @RequestParam String platform,
            @RequestParam(name = "current_version") String currentVersion) {

        log.info("版本检查请求 platform={}, currentVersion={}", platform, currentVersion);

        if (platform == null || currentVersion == null) {
            log.warn("版本检查缺少参数 platform={}, currentVersion={}", platform, currentVersion);
            return ApiResponse.error(400, "platform 和 current_version 不能为空");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("latest_version", "1.0.1");
        data.put("min_version", "1.0.0");
        data.put("download_url", "https://example.com/download");
        data.put("release_notes", "修复了一些问题");
        data.put("force_update", false);
        return ApiResponse.success(data);
    }

    /**
     * 提交反馈
     * POST /feedback
     * Body: {
     *   "content": "反馈内容",
     *   "contact": "联系方式(可选)",
     *   "images": ["图片URL1", "图片URL2"]
     * }
     */
    @Operation(summary = "提交反馈", description = "用户提交反馈意见")
    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> submitFeedback(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody Map<String, Object> body) {

        String content = (String) body.get("content");
        if (content == null || content.trim().isEmpty()) {
            log.warn("提交反馈缺少内容 userId={}", currentUserId);
            return ApiResponse.error(400, "content 不能为空");
        }

        String contact = (String) body.get("contact");
        Object images = body.get("images");

        // TODO: 保存反馈到数据库
        Long feedbackId = System.currentTimeMillis();
        log.info("收到反馈 userId={}, contact={}, hasImages={}", currentUserId, contact, images != null);

        Map<String, Object> result = new HashMap<>();
        result.put("feedback_id", feedbackId);
        result.put("created_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .format(new java.util.Date()));
        
        return ApiResponse.success(result);
    }

    /**
     * 检查设备是否在库中
     * GET /api/app/device/exists?device_id=xxx
     * 用于APP扫描机身码时判断设备是否在manufactured_device表中
     */
    @Operation(summary = "检查设备是否在库中", description = "根据设备ID判断设备是否已入库(manufactured_device表)")
    @GetMapping("/device/exists")
    public ApiResponse<Map<String, Object>> checkDeviceExists(
            @Parameter(description = "设备ID(机身码)") @RequestParam(name = "device_id") String deviceId) {

        log.info("检查设备是否在库 deviceId={}", deviceId);

        if (deviceId == null || deviceId.trim().isEmpty()) {
            log.warn("检查设备缺少参数 deviceId={}", deviceId);
            return ApiResponse.error(400, "device_id 不能为空");
        }

        ManufacturedDevice device = deviceProductionService.getDevice(deviceId.trim());
        boolean exists = device != null;

        Map<String, Object> data = new HashMap<>();
        data.put("exists", exists);
        data.put("device_id", deviceId);
        if (exists) {
            data.put("status", device.getStatus() != null ? device.getStatus().getCode() : null);
        }

        log.info("检查设备结果 deviceId={}, exists={}", deviceId, exists);
        return ApiResponse.success(data);
    }
}
