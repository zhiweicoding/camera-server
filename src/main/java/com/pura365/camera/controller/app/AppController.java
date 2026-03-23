package com.pura365.camera.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.AppVersion;
import com.pura365.camera.domain.ManufacturedDevice;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.repository.AppVersionRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.service.DeviceProductionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
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

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private AppVersionRepository appVersionRepository;

    @Value("${app.startup.firebase.android-enabled:false}")
    private boolean firebaseAndroidEnabled;

    @Value("${app.startup.firebase.ios-enabled:true}")
    private boolean firebaseIosEnabled;

    /**
     * 版本检查
     * GET /api/app/version?platform=ios&current_version=1.0.0
     */
    @Operation(summary = "版本检查", description = "检查App是否需要更新")
    @GetMapping("/version")
    public ApiResponse<Map<String, Object>> checkVersion(
            @RequestParam String platform,
            @RequestParam(name = "current_version", required = false) String currentVersion) {

        log.info("版本检查请求 platform={}, currentVersion={}", platform, currentVersion);

        if (platform == null || platform.trim().isEmpty()) {
            log.warn("版本检查缺少参数 platform={}", platform);
            return ApiResponse.error(400, "platform 不能为空");
        }

        String normalizedPlatform = platform.trim().toLowerCase();
        if (!"android".equals(normalizedPlatform) && !"ios".equals(normalizedPlatform)) {
            return ApiResponse.error(400, "platform 仅支持 android 或 ios");
        }
        boolean enableFirebase = isFirebaseEnabled(normalizedPlatform);

        LambdaQueryWrapper<AppVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .apply("LOWER(platform) = {0}", normalizedPlatform)
                .orderByDesc(AppVersion::getId)
                .last("LIMIT 1");

        AppVersion latest = appVersionRepository.selectOne(queryWrapper);
        if (latest == null) {
            log.warn("未找到版本配置 platform={}", normalizedPlatform);
            Map<String, Object> data = new HashMap<>();
            String safeCurrentVersion = sanitizeVersion(currentVersion, "1.0.0");
            data.put("latest_version", safeCurrentVersion);
            data.put("latest_version_code", toVersionCode(safeCurrentVersion));
            data.put("min_version", safeCurrentVersion);
            data.put("min_version_code", toVersionCode(safeCurrentVersion));
            data.put("version", safeCurrentVersion);
            data.put("version_code", toVersionCode(safeCurrentVersion));
            data.put("force_update", false);
            data.put("has_update", false);
            data.put("update_description", "");
            data.put("release_notes", "");
            data.put("download_url", "");
            data.put("apk_url", "");
            data.put("app_store_url", "");
            data.put("platform", normalizedPlatform);
            data.put("enable_firebase", enableFirebase);
            return ApiResponse.success(data);
        }

        String latestVersion = sanitizeVersion(latest.getLatestVersion(), "1.0.0");
        String minVersion = sanitizeVersion(latest.getMinVersion(), latestVersion);
        String safeCurrentVersion = sanitizeVersion(currentVersion, latestVersion);

        boolean hasUpdate = compareVersion(latestVersion, safeCurrentVersion) > 0;
        boolean forceByConfig = latest.getForceUpdate() != null && latest.getForceUpdate() == 1;
        boolean forceByMinVersion = compareVersion(safeCurrentVersion, minVersion) < 0;
        boolean forceUpdate = forceByConfig || forceByMinVersion;

        String releaseDate = formatDate(latest.getUpdatedAt() != null ? latest.getUpdatedAt() : latest.getCreatedAt());
        String downloadUrl = latest.getDownloadUrl() == null ? "" : latest.getDownloadUrl();
        String appStoreUrl = "ios".equals(normalizedPlatform) ? downloadUrl : "";
        String apkUrl = "android".equals(normalizedPlatform) ? downloadUrl : "";

        Map<String, Object> data = new HashMap<>();
        data.put("latest_version", latestVersion);
        data.put("latest_version_code", toVersionCode(latestVersion));
        data.put("min_version", minVersion);
        data.put("min_version_code", toVersionCode(minVersion));
        data.put("version", latestVersion);
        data.put("version_code", toVersionCode(latestVersion));
        data.put("force_update", forceUpdate);
        data.put("has_update", hasUpdate);
        data.put("update_description", latest.getReleaseNotes() == null ? "" : latest.getReleaseNotes());
        data.put("release_notes", latest.getReleaseNotes() == null ? "" : latest.getReleaseNotes());
        data.put("download_url", downloadUrl);
        data.put("apk_url", apkUrl);
        data.put("app_store_url", appStoreUrl);
        data.put("release_date", releaseDate);
        data.put("platform", normalizedPlatform);
        data.put("enable_firebase", enableFirebase);
        return ApiResponse.success(data);
    }

    private boolean isFirebaseEnabled(String normalizedPlatform) {
        if ("ios".equals(normalizedPlatform)) {
            return firebaseIosEnabled;
        }
        return firebaseAndroidEnabled;
    }

    private String sanitizeVersion(String version, String fallback) {
        if (version == null || version.trim().isEmpty()) {
            return fallback;
        }
        return version.trim();
    }

    private Integer toVersionCode(String version) {
        String[] parts = sanitizeVersion(version, "0.0.0").split("\\.");
        int major = parsePart(parts, 0);
        int minor = parsePart(parts, 1);
        int patch = parsePart(parts, 2);
        return major * 10000 + minor * 100 + patch;
    }

    private int parsePart(String[] parts, int index) {
        if (parts == null || index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private int compareVersion(String v1, String v2) {
        String[] p1 = sanitizeVersion(v1, "0.0.0").split("\\.");
        String[] p2 = sanitizeVersion(v2, "0.0.0").split("\\.");
        int maxLen = Math.max(p1.length, p2.length);
        for (int i = 0; i < maxLen; i++) {
            int n1 = parsePart(p1, i);
            int n2 = parsePart(p2, i);
            if (n1 != n2) {
                return n1 - n2;
            }
        }
        return 0;
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date);
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

//        // 检查设备是否已被绑定
//        Long bindCount = userDeviceRepository.selectCount(
//                new LambdaQueryWrapper<UserDevice>().eq(UserDevice::getDeviceId, deviceId.trim()));
//        boolean bound = bindCount != null && bindCount > 0;
        data.put("bound", false);
//        if (bound) {
//            log.info("设备已被绑定 deviceId={}", deviceId);
//        }

        log.info("检查设备结果 deviceId={}, exists={}", deviceId, exists);
        return ApiResponse.success(data);
    }
}
