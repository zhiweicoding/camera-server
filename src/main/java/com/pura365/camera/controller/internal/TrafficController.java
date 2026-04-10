package com.pura365.camera.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.DeviceTrafficSim;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.DeviceTrafficSimRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.service.LinksFieldTrafficService;
import com.pura365.camera.service.TrafficPreviewPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 4G 流量接口（后端代理 LinksField）
 */
@Tag(name = "4G流量接口", description = "查询设备4G实时剩余流量")
@RestController
@RequestMapping("/api/internal/traffic")
public class TrafficController {

    private static final Logger log = LoggerFactory.getLogger(TrafficController.class);
    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceTrafficSimRepository deviceTrafficSimRepository;

    @Autowired
    private LinksFieldTrafficService linksFieldTrafficService;

    @Autowired
    private TrafficPreviewPolicyService trafficPreviewPolicyService;

    @Operation(summary = "查询设备4G实时剩余流量")
    @GetMapping("/devices/{id}/remaining-data")
    public ApiResponse<Map<String, Object>> getRemainingData(
            @RequestAttribute("currentUserId") Long currentUserId,
            @PathVariable("id") String deviceId) {

        log.info("查询设备4G实时剩余流量 - userId={}, deviceId={}", currentUserId, deviceId);
        if (!StringUtils.hasText(deviceId)) {
            log.warn("查询设备4G实时剩余流量失败 - deviceId为空, userId={}", currentUserId);
            return ApiResponse.error(400, "device_id 不能为空");
        }
        if (!hasUserDevice(currentUserId, deviceId)) {
            log.warn("查询设备4G实时剩余流量失败 - 无权限, userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(403, "无权查看该设备");
        }

        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.warn("查询设备4G实时剩余流量失败 - 设备不存在, userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(404, "设备不存在");
        }

        String simId = findSimId(deviceId);
        if (!StringUtils.hasText(simId)) {
            log.warn("查询设备4G实时剩余流量失败 - 未配置simId, userId={}, deviceId={}", currentUserId, deviceId);
            return ApiResponse.error(400, "该设备未配置 sim_id");
        }

        try {
            Map<String, Object> thirdResult = linksFieldTrafficService.queryRemainingData(simId);
            Map<String, Object> responseData = buildTrafficResponse(deviceId, simId, thirdResult);
            log.info("查询设备4G实时剩余流量成功 - userId={}, deviceId={}, simId={}, response={}",
                    currentUserId, deviceId, simId, responseData);
            return ApiResponse.success(responseData);
        } catch (Exception e) {
            log.error("查询设备4G流量失败 - userId={}, deviceId={}", currentUserId, deviceId, e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @Operation(summary = "查询设备预览流量策略")
    @GetMapping("/devices/{id}/preview-policy")
    public ApiResponse<Map<String, Object>> getPreviewPolicy(
            @RequestAttribute("currentUserId") Long currentUserId,
            @PathVariable("id") String deviceId) {

        log.info("查询设备预览流量策略 - userId={}, deviceId={}", currentUserId, deviceId);
        TrafficPreviewPolicyService.PolicyEvaluation evaluation =
                trafficPreviewPolicyService.evaluate(currentUserId, deviceId);
        if (!evaluation.isOk()) {
            log.warn("查询设备预览流量策略失败 - userId={}, deviceId={}, httpStatus={}, error={}",
                    currentUserId, deviceId, evaluation.getHttpStatus(), evaluation.getErrorMessage());
            return ApiResponse.error(evaluation.getHttpStatus(), evaluation.getErrorMessage());
        }
        log.info("查询设备预览流量策略成功 - userId={}, deviceId={}, policy={}",
                currentUserId, deviceId, evaluation.getPolicy());
        return ApiResponse.success(evaluation.getPolicy());
    }

    @Operation(summary = "设置设备SIM ID")
    @PutMapping("/devices/{id}/sim-id")
    public ApiResponse<Map<String, Object>> upsertDeviceSimId(
            @RequestAttribute("currentUserId") Long currentUserId,
            @PathVariable("id") String deviceId,
            @RequestBody Map<String, Object> body) {

        if (!StringUtils.hasText(deviceId)) {
            return ApiResponse.error(400, "device_id 不能为空");
        }
        if (!hasUserDevice(currentUserId, deviceId)) {
            return ApiResponse.error(403, "无权操作该设备");
        }

        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }

        Object simIdObj = body != null ? body.get("sim_id") : null;
        String simId = simIdObj == null ? null : String.valueOf(simIdObj).trim();
        if (!StringUtils.hasText(simId)) {
            return ApiResponse.error(400, "sim_id 不能为空");
        }

        device.setIccid(simId);
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.updateById(device);

        QueryWrapper<DeviceTrafficSim> qw = new QueryWrapper<DeviceTrafficSim>();
        qw.lambda().eq(DeviceTrafficSim::getDeviceId, deviceId);
        DeviceTrafficSim existing = deviceTrafficSimRepository.selectOne(qw);

        Date now = new Date();
        if (existing == null) {
            existing = new DeviceTrafficSim();
            existing.setDeviceId(deviceId);
            existing.setSimId(simId);
            existing.setCreatedAt(now);
            existing.setUpdatedAt(now);
            deviceTrafficSimRepository.insert(existing);
        } else {
            existing.setSimId(simId);
            existing.setUpdatedAt(now);
            deviceTrafficSimRepository.updateById(existing);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("device_id", deviceId);
        result.put("sim_id", simId);
        return ApiResponse.success(result);
    }

    private boolean hasUserDevice(Long userId, String deviceId) {
        QueryWrapper<UserDevice> query = new QueryWrapper<UserDevice>();
        query.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        return userDeviceRepository.selectCount(query) > 0;
    }

    private String findSimId(String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device != null && StringUtils.hasText(device.getIccid())) {
            return device.getIccid().trim();
        }
        QueryWrapper<DeviceTrafficSim> query = new QueryWrapper<DeviceTrafficSim>();
        query.lambda().eq(DeviceTrafficSim::getDeviceId, deviceId);
        DeviceTrafficSim mapping = deviceTrafficSimRepository.selectOne(query);
        return mapping == null ? null : mapping.getSimId();
    }

    private Map<String, Object> buildTrafficResponse(String deviceId,
                                                     String simId,
                                                     Map<String, Object> thirdResult) {
        Map<String, Object> responseData = new LinkedHashMap<String, Object>();
        responseData.put("device_id", deviceId);
        responseData.put("sim_id", simId);

        Object thirdData = thirdResult.get("data");
        if (thirdData instanceof Map<?, ?>) {
            responseData.put("query_source", "remaining_data");
            Map<?, ?> mapData = (Map<?, ?>) thirdData;
            for (Map.Entry<?, ?> entry : mapData.entrySet()) {
                if (entry.getKey() != null) {
                    responseData.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return responseData;
        }

        if (thirdData instanceof Iterable<?>) {
            responseData.put("query_source", "bundles");
            normalizeBundleData((Iterable<?>) thirdData, responseData);
            responseData.put("raw", thirdResult);
            return responseData;
        }

        responseData.put("query_source", "unknown");
        responseData.put("raw", thirdResult);
        return responseData;
    }

    private void normalizeBundleData(Iterable<?> bundles, Map<String, Object> responseData) {
        long totalBytes = 0L;
        long usedBytes = 0L;
        int packageCount = 0;
        boolean hasTotal = false;
        boolean hasUsed = false;
        Long startAt = null;
        Long expireAt = null;

        for (Object item : bundles) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            packageCount++;
            Map<?, ?> bundle = (Map<?, ?>) item;

            if (packageCount == 1) {
                copyBundleField(bundle, responseData, "id", "bundle_id");
                copyBundleField(bundle, responseData, "order_id", "order_id");
                copyBundleField(bundle, responseData, "status", "status");
                copyBundleField(bundle, responseData, "type", "type");
                copyBundleField(bundle, responseData, "use", "use");
                copyBundleField(bundle, responseData, "period_unit", "period_unit");
                copyBundleField(bundle, responseData, "period_number", "period_number");
                copyBundleField(bundle, responseData, "current_cycle", "current_cycle");
            }

            Long limit = toLong(bundle.get("data_limit"));
            if (limit != null && limit >= 0) {
                totalBytes += limit;
                hasTotal = true;
            }

            Long usage = toLong(bundle.get("current_cycle_usage"));
            if (usage != null && usage >= 0) {
                usedBytes += usage;
                hasUsed = true;
            }

            Long bundleStartAt = firstPositiveLong(bundle.get("current_cycle_start_at"), bundle.get("start_at"));
            if (bundleStartAt != null && (startAt == null || bundleStartAt < startAt.longValue())) {
                startAt = bundleStartAt;
            }

            Long bundleExpireAt = firstPositiveLong(bundle.get("current_cycle_end_at"), bundle.get("end_at"));
            if (bundleExpireAt != null && (expireAt == null || bundleExpireAt > expireAt.longValue())) {
                expireAt = bundleExpireAt;
            }
        }

        responseData.put("package_count", packageCount);
        if (startAt != null) {
            responseData.put("start_at", formatEpochMillis(startAt.longValue()));
        }
        if (expireAt != null) {
            responseData.put("expire_at", formatEpochMillis(expireAt.longValue()));
        }

        if (hasTotal) {
            responseData.put("total_data_bytes", totalBytes);
            responseData.put("total_data", formatBytes(totalBytes));
        }

        if (hasUsed) {
            responseData.put("used_data_bytes", usedBytes);
            responseData.put("used_data", formatBytes(usedBytes));
        }

        if (hasTotal) {
            long remainingBytes = hasUsed ? Math.max(0L, totalBytes - usedBytes) : totalBytes;
            responseData.put("remaining_data_bytes", remainingBytes);
            responseData.put("remaining_data", formatBytes(remainingBytes));
            if (hasUsed && totalBytes > 0) {
                responseData.put("usage_percent", round2(usedBytes * 100.0 / totalBytes));
            }
        }
    }

    private void copyBundleField(Map<?, ?> source, Map<String, Object> target, String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private Long firstPositiveLong(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Long parsed = toLong(value);
            if (parsed != null && parsed.longValue() > 0L) {
                return parsed;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatEpochMillis(long epochMillis) {
        try {
            return ISO_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC));
        } catch (Exception e) {
            return String.valueOf(epochMillis);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "--";
        }

        double value = bytes;
        String unit = "B";
        if (bytes >= BYTES_PER_GB) {
            value = value / BYTES_PER_GB;
            unit = "GB";
        } else if (bytes >= BYTES_PER_MB) {
            value = value / BYTES_PER_MB;
            unit = "MB";
        } else if (bytes >= BYTES_PER_KB) {
            value = value / BYTES_PER_KB;
            unit = "KB";
        }

        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = value >= 100 ? new DecimalFormat("0", symbols) : new DecimalFormat("0.##", symbols);
        return format.format(value) + " " + unit;
    }

    private Double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
