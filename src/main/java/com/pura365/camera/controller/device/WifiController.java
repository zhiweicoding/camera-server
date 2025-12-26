package com.pura365.camera.controller.device;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.wifi.BindDeviceRequest;
import com.pura365.camera.model.wifi.BindingStatusVO;
import com.pura365.camera.model.wifi.WifiInfoVO;
import com.pura365.camera.service.WifiService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * WiFi 配网 & 设备绑定相关接口
 *
 * - GET /wifi/scan
 * - POST /devices/bind
 * - GET /devices/{id}/binding-status
 */
@Tag(name = "WiFi管理", description = "WiFi配置相关接口")
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class WifiController {

    private static final Logger log = LoggerFactory.getLogger(WifiController.class);

    private final WifiService wifiService;

    /**
     * WiFi 列表 - GET /wifi/scan
     *
     * 目前从 wifi_history 返回当前用户最近使用的 WiFi 记录
     */
    @Operation(summary = "扫描 WiFi 列表", description = "返回当前用户最近使用的 WiFi 记录")
    @GetMapping("/wifi/scan")
    public ApiResponse<List<WifiInfoVO>> scanWifi(@RequestAttribute("currentUserId") Long currentUserId) {
        log.info("[WiFi] 扫描WiFi列表请求, userId={}", currentUserId);
        try {
            List<WifiInfoVO> result = wifiService.getWifiHistory(currentUserId);
            log.info("[WiFi] 扫描WiFi列表成功, userId={}, count={}", currentUserId, result.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[WiFi] 扫描WiFi列表失败, userId={}", currentUserId, e);
            return ApiResponse.error(500, "获取WiFi列表失败: " + e.getMessage());
        }
    }

    /**
     * 设备绑定 - POST /devices/bind
     */
    @Operation(summary = "设备绑定", description = "绑定设备并记录 WiFi 信息")
    @PostMapping("/devices/bind")
    public ApiResponse<BindingStatusVO> bindDevice(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Valid @RequestBody BindDeviceRequest request) {
        log.info("[WiFi] 设备绑定请求, userId={}, deviceSn={}, wifiSsid={}", 
                currentUserId, request.getDeviceSn(), request.getWifiSsid());
        
        if (request.getDeviceSn() == null || request.getDeviceSn().isEmpty()) {
            log.warn("[WiFi] 设备绑定失败: 设备序列号为空, userId={}", currentUserId);
            return ApiResponse.error(400, "device_sn 不能为空");
        }
        
        try {
            BindingStatusVO result = wifiService.bindDevice(currentUserId, request);
            log.info("[WiFi] 设备绑定成功, userId={}, deviceId={}", currentUserId, result.getDeviceId());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[WiFi] 设备绑定失败, userId={}, deviceSn={}", 
                    currentUserId, request.getDeviceSn(), e);
            return ApiResponse.error(500, "设备绑定失败: " + e.getMessage());
        }
    }

    /**
     * 查询绑定进度 - GET /devices/{id}/binding-status
     */
    @Operation(summary = "查询绑定进度", description = "查询设备绑定进度和状态")
    @GetMapping("/devices/{id}/binding-status")
    public ApiResponse<BindingStatusVO> getBindingStatus(
            @RequestAttribute("currentUserId") Long currentUserId,
            @PathVariable("id") String deviceId) {
        log.debug("[WiFi] 查询绑定状态, userId={}, deviceId={}", currentUserId, deviceId);
        
        try {
            BindingStatusVO result = wifiService.getBindingStatus(currentUserId, deviceId);
            log.debug("[WiFi] 查询绑定状态成功, deviceId={}, status={}", deviceId, result.getStatus());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[WiFi] 查询绑定状态失败, userId={}, deviceId={}", currentUserId, deviceId, e);
            return ApiResponse.error(500, "查询绑定状态失败: " + e.getMessage());
        }
    }
}
