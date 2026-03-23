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

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 4G 流量接口（后端代理 LinksField）
 */
@Tag(name = "4G流量接口", description = "查询设备4G实时剩余流量")
@RestController
@RequestMapping("/api/internal/traffic")
public class TrafficController {

    private static final Logger log = LoggerFactory.getLogger(TrafficController.class);

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

        if (!StringUtils.hasText(deviceId)) {
            return ApiResponse.error(400, "device_id 不能为空");
        }
        if (!hasUserDevice(currentUserId, deviceId)) {
            return ApiResponse.error(403, "无权查看该设备");
        }

        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }

        String simId = findSimId(deviceId);
        if (!StringUtils.hasText(simId)) {
            return ApiResponse.error(400, "该设备未配置 sim_id");
        }

        try {
            Map<String, Object> thirdResult = linksFieldTrafficService.queryRemainingData(simId);
            Map<String, Object> responseData = new LinkedHashMap<String, Object>();
            responseData.put("device_id", deviceId);
            responseData.put("sim_id", simId);

            Object thirdData = thirdResult.get("data");
            if (thirdData instanceof Map<?, ?>) {
                Map<?, ?> mapData = (Map<?, ?>) thirdData;
                for (Map.Entry<?, ?> entry : mapData.entrySet()) {
                    if (entry.getKey() != null) {
                        responseData.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            } else {
                responseData.put("raw", thirdResult);
            }
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

        TrafficPreviewPolicyService.PolicyEvaluation evaluation =
                trafficPreviewPolicyService.evaluate(currentUserId, deviceId);
        if (!evaluation.isOk()) {
            return ApiResponse.error(evaluation.getHttpStatus(), evaluation.getErrorMessage());
        }
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
}
