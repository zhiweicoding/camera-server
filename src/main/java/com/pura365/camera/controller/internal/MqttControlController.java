package com.pura365.camera.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.domain.Device;
import com.pura365.camera.dto.BulbConfigRequest;
import com.pura365.camera.dto.BulbConfigResponse;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.mqtt.MqttBulbConfigMessage;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.service.AuthService;
import com.pura365.camera.service.MqttMessageService;
import com.pura365.camera.service.TrafficPreviewPolicyService;
import com.pura365.camera.util.TimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT 控制接口
 * 提供 HTTP API，APP 可以通过后端控制摄像头
 */
@Tag(name = "MQTT控制", description = "MQTT消息控制接口")
@RestController
@RequestMapping("/api/internal/mqtt")
public class MqttControlController {

    private static final Logger log = LoggerFactory.getLogger(MqttControlController.class);
    
    // 等待设备响应超时时间（毫秒）
    private static final long DEVICE_RESPONSE_TIMEOUT = 5000;

    @Autowired
    private MqttMessageService mqttMessageService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private TrafficPreviewPolicyService trafficPreviewPolicyService;

    /**
     * 请求设备信息（CODE 11）
     */
    @Operation(summary = "请求设备信息", description = "通过 MQTT 请求设备上报基本信息")
    @PostMapping("/device/{deviceId}/info")
    public ResponseEntity<Map<String, Object>> requestDeviceInfo(
            @PathVariable String deviceId) {

        log.info("请求设备 {} 的信息", deviceId);

        try {
            mqttMessageService.requestDeviceInfo(deviceId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送请求");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("请求设备信息失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 格式化 TF 卡（CODE 12）
     */
    @Operation(summary = "格式化 TF 卡", description = "通过 MQTT 请求设备格式化 TF 卡")
    @PostMapping("/device/{deviceId}/format")
    public ResponseEntity<Map<String, Object>> formatSdCard(
            @PathVariable String deviceId) {

        log.info("请求格式化设备 {} 的 TF 卡", deviceId);

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 12);
            msg.put("time", TimeValidator.getCurrentTimestamp());

            mqttMessageService.sendToDevice(deviceId, msg, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送格式化指令");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("格式化 TF 卡失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 重启摄像头（CODE 13）
     */
    @Operation(summary = "重启设备", description = "通过 MQTT 请求设备重启")
    @PostMapping("/device/{deviceId}/reboot")
    public ResponseEntity<Map<String, Object>> rebootDevice(
            @PathVariable String deviceId) {

        log.info("请求重启设备 {}", deviceId);

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 13);
            msg.put("time", TimeValidator.getCurrentTimestamp());

            mqttMessageService.sendToDevice(deviceId, msg, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送重启指令");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("重启设备失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 设置画面旋转（CODE 26）
     */
    @Operation(summary = "设置画面旋转", description = "设置摄像头画面是否旋转 180°")
    @PostMapping("/device/{deviceId}/rotate")
    public ResponseEntity<Map<String, Object>> setRotate(
            @PathVariable String deviceId,
            @RequestParam Integer enable) { // 0: 不旋转  1: 旋转180°

        log.info("设置设备 {} 画面旋转: {}", deviceId, enable);

        try {
            // 1. 先更新数据库（乐观更新）
            Device device = deviceRepository.selectById(deviceId);
            if (device != null) {
                device.setRotate(enable);
                device.setUpdatedAt(java.time.LocalDateTime.now());
                deviceRepository.updateById(device);
                log.info("已更新设备 {} 画面旋转状态到数据库: {}", deviceId, enable);
            }

            // 2. 再发送 MQTT 消息到设备
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 26);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            msg.put("enable", enable);

            mqttMessageService.sendToDevice(deviceId, msg, null);

            // 3. 返回结果（包含新状态）
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送旋转设置");
            result.put("rotate", enable);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("设置旋转失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 设置白光灯使能（CODE 28）
     */
    @Operation(summary = "设置白光灯", description = "开启或关闭摄像头白光灯")
    @PostMapping("/device/{deviceId}/whiteled")
    public ResponseEntity<Map<String, Object>> setWhiteLed(
            @PathVariable String deviceId,
            @RequestParam Integer enable) { // 0: 禁用, 1: 启用

        log.info("设置设备 {} 白光灯: {}", deviceId, enable);

        try {
            // 1. 先更新数据库（乐观更新）
            Device device = deviceRepository.selectById(deviceId);
            if (device != null) {
                device.setWhiteLed(enable);
                device.setUpdatedAt(java.time.LocalDateTime.now());
                deviceRepository.updateById(device);
                log.info("已更新设备 {} 白光灯状态到数据库: {}", deviceId, enable);
            }

            // 2. 再发送 MQTT 消息到设备
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 28);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            msg.put("enable", enable);

            mqttMessageService.sendToDevice(deviceId, msg, null);

            // 3. 返回结果（包含新状态）
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送白光灯设置");
            result.put("whiteLed", enable);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("设置白光灯失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 请求 WebRTC Offer（CODE 23）
     */
    @Operation(summary = "请求 WebRTC Offer", description = "请求摄像头通过 MQTT 返回 WebRTC Offer")
    @PostMapping("/device/{deviceId}/webrtc/offer")
    public ResponseEntity<Map<String, Object>> requestWebRtcOffer(
            @PathVariable String deviceId,
            @RequestParam String sid, // Peer ID
            @RequestParam String rtcServer, // 格式: server,user,pass
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {

        log.info("请求设备 {} 的WebRTC Offer - SID: {}", deviceId, sid);

        Long resolvedUserId = resolveCurrentUserId(currentUserId, authorization, request);
        if (resolvedUserId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录或登录已过期");
            return ResponseEntity.status(401).body(result);
        }

        TrafficPreviewPolicyService.PolicyEvaluation evaluation =
                trafficPreviewPolicyService.evaluate(resolvedUserId, deviceId);
        if (!evaluation.isOk()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", evaluation.getErrorMessage());
            return ResponseEntity.status(evaluation.getHttpStatus()).body(result);
        }

        Map<String, Object> policy = evaluation.getPolicy();
        if (policy != null && Boolean.FALSE.equals(policy.get("can_preview"))) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", stringValue(policy.get("reason_message"), "当前设备不可预览"));
            result.put("reason_code", policy.get("reason_code"));
            result.put("policy", policy);
            return ResponseEntity.status(403).body(result);
        }

        try {
            mqttMessageService.requestWebRtcOffer(deviceId, sid, rtcServer);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已请求 WebRTC Offer");
            result.put("policy", policy);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("请求 WebRTC Offer 失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 发送 WebRTC Answer（CODE 24）
     */
    @Operation(summary = "发送 WebRTC Answer", description = "通过 MQTT 向摄像头发送 WebRTC Answer")
    @PostMapping("/device/{deviceId}/webrtc/answer")
    public ResponseEntity<Map<String, Object>> sendWebRtcAnswer(
            @PathVariable String deviceId,
            @RequestParam String sid,
            @RequestParam String sdp) {

        log.info("发送 WebRTC Answer 到设备 {} - SID: {}", deviceId, sid);

        try {
            mqttMessageService.sendWebRtcAnswer(deviceId, sid, sdp);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送 WebRTC Answer");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("发送 WebRTC Answer 失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 发送 WebRTC Candidate（CODE 25）
     */
    @Operation(summary = "发送 WebRTC Candidate", description = "通过 MQTT 向摄像头发送 WebRTC Candidate")
    @PostMapping("/device/{deviceId}/webrtc/candidate")
    public ResponseEntity<Map<String, Object>> sendWebRtcCandidate(
            @PathVariable String deviceId,
            @RequestParam String sid,
            @RequestParam String candidate) {

        log.info("发送 WebRTC Candidate 到设备 {} - SID: {}", deviceId, sid);

        try {
            mqttMessageService.sendWebRtcCandidate(deviceId, sid, candidate);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送 WebRTC Candidate");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("发送 WebRTC Candidate 失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 发送 WebRTC Candidate 更新（CODE 159 = 128+31）
     * 用于 Answer 发送后更新 Offer 端的 Candidate 信息
     */
    @Operation(summary = "发送 WebRTC Candidate159", description = "通过 MQTT 向摄像头发送 WebRTC Candidate 更新（Answer 后）")
    @PostMapping("/device/{deviceId}/webrtc/candidate159")
    public ResponseEntity<Map<String, Object>> sendWebRtcCandidate159(
            @PathVariable String deviceId,
            @RequestParam String sid,
            @RequestParam String candidate) {

        log.info("发送 WebRTC Candidate159 到设备 {} - SID: {}", deviceId, sid);

        try {
            mqttMessageService.sendWebRtcCandidate159(deviceId, sid, candidate);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送 WebRTC Candidate159");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("发送 WebRTC Candidate159 失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 设置灯泡参数（CODE 29）
     */
    @Operation(summary = "设置灯泡参数", description = "配置灯泡工作模式、亮度、定时等参数")
    @PostMapping("/device/{deviceId}/bulb/config")
    public ApiResponse<BulbConfigResponse> setBulbConfig(
            @PathVariable String deviceId,
            @Valid @RequestBody BulbConfigRequest request) {

        log.info("设置设备 {} 灯泡参数: detect={}, brightness={}", 
                deviceId, request.getDetect(), request.getBrightness());

        // 检查设备是否存在
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }

        try {
            // 构建 MQTT 消息参数
            Map<String, Object> config = new HashMap<>();
            config.put("detect", request.getDetect());

            if (request.getBrightness() != null) {
                config.put("brightness", request.getBrightness());
            }
            if (request.getEnable() != null) {
                config.put("enable", request.getEnable());
            }
            if (request.getTimeOn1() != null) {
                config.put("time_on1", request.getTimeOn1());
            }
            if (request.getTimeOff1() != null) {
                config.put("time_off1", request.getTimeOff1());
            }
            if (request.getTimeOn2() != null) {
                config.put("time_o2", request.getTimeOn2());  // 注意接口文档中是 time_o2
            }
            if (request.getTimeOff2() != null) {
                config.put("time_off2", request.getTimeOff2());
            }

            // 发送 MQTT 消息并等待响应
            MqttBulbConfigMessage response = mqttMessageService.setBulbConfigAndWait(
                    deviceId, config, DEVICE_RESPONSE_TIMEOUT);

            if (response == null) {
                // 超时，但乐观更新数据库
                updateDeviceBulbConfig(device, request);
                log.warn("设备 {} 响应超时，已乐观更新数据库", deviceId);
                
                BulbConfigResponse result = buildResponseFromRequest(request);
                return ApiResponse.success(result);
            }

            if (response.getStatus() != null && response.getStatus() == 1) {
                // 配置成功，更新数据库
                updateDeviceBulbConfig(device, request);
                
                BulbConfigResponse result = buildResponseFromRequest(request);
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error(500, "设备配置失败");
            }

        } catch (Exception e) {
            log.error("设置灯泡参数失败", e);
            return ApiResponse.error(500, "设置失败: " + e.getMessage());
        }
    }

    /**
     * 获取灯泡配置（CODE 30）
     */
    @Operation(summary = "获取灯泡配置", description = "获取灯泡当前的配置参数")
    @GetMapping("/device/{deviceId}/bulb/config")
    public ApiResponse<BulbConfigResponse> getBulbConfig(
            @PathVariable String deviceId) {

        log.info("获取设备 {} 灯泡配置", deviceId);

        // 检查设备是否存在
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }

        try {
            MqttBulbConfigMessage response = mqttMessageService.getBulbConfigAndWait(
                    deviceId, DEVICE_RESPONSE_TIMEOUT);

            if (response != null && response.getStatus() != null && response.getStatus() == 1) {
                // 设备返回了最新配置，已在 MqttMessageService 中更新数据库
                BulbConfigResponse result = buildResponseFromMqtt(response);
                return ApiResponse.success(result);
            }
            // 超时或失败，继续返回数据库中的数据
            log.warn("设备 {} 响应超时，返回数据库缓存配置", deviceId);
        } catch (Exception e) {
            log.warn("获取设备 {} 最新配置失败: {}", deviceId, e.getMessage());
        }

        // 从数据库返回缓存的配置
        BulbConfigResponse result = buildResponseFromDevice(device);
        return ApiResponse.success(result);
    }

    /**
     * 更新数据库中的灯泡配置
     */
    private void updateDeviceBulbConfig(Device device, BulbConfigRequest request) {
        device.setBulbDetect(request.getDetect());
        if (request.getBrightness() != null) {
            device.setBulbBrightness(request.getBrightness());
        }
        if (request.getEnable() != null) {
            device.setBulbEnable(request.getEnable());
        }
        if (request.getTimeOn1() != null) {
            device.setBulbTimeOn1(request.getTimeOn1());
        }
        if (request.getTimeOff1() != null) {
            device.setBulbTimeOff1(request.getTimeOff1());
        }
        if (request.getTimeOn2() != null) {
            device.setBulbTimeOn2(request.getTimeOn2());
        }
        if (request.getTimeOff2() != null) {
            device.setBulbTimeOff2(request.getTimeOff2());
        }
        device.setUpdatedAt(LocalDateTime.now());
        deviceRepository.updateById(device);
    }

    /**
     * 从请求构建响应
     */
    private BulbConfigResponse buildResponseFromRequest(BulbConfigRequest request) {
        BulbConfigResponse response = new BulbConfigResponse();
        response.setDetect(request.getDetect());
        response.setBrightness(request.getBrightness());
        response.setEnable(request.getEnable());
        response.setTimeOn1(request.getTimeOn1());
        response.setTimeOff1(request.getTimeOff1());
        response.setTimeOn2(request.getTimeOn2());
        response.setTimeOff2(request.getTimeOff2());
        return response;
    }

    /**
     * 从 MQTT 响应构建返回对象
     */
    private BulbConfigResponse buildResponseFromMqtt(MqttBulbConfigMessage msg) {
        BulbConfigResponse response = new BulbConfigResponse();
        response.setDetect(msg.getDetect());
        response.setBrightness(msg.getBrightness());
        response.setEnable(msg.getEnable());
        response.setTimeOn1(msg.getTimeOn1());
        response.setTimeOff1(msg.getTimeOff1());
        response.setTimeOn2(msg.getTimeOn2());
        response.setTimeOff2(msg.getTimeOff2());
        return response;
    }

    /**
     * 从数据库设备对象构建返回对象
     */
    private BulbConfigResponse buildResponseFromDevice(Device device) {
        BulbConfigResponse response = new BulbConfigResponse();
        response.setDetect(device.getBulbDetect());
        response.setBrightness(device.getBulbBrightness());
        response.setEnable(device.getBulbEnable());
        response.setTimeOn1(device.getBulbTimeOn1());
        response.setTimeOff1(device.getBulbTimeOff1());
        response.setTimeOn2(device.getBulbTimeOn2());
        response.setTimeOff2(device.getBulbTimeOff2());
        return response;
    }

    private Long resolveCurrentUserId(Long currentUserId, String authorization, HttpServletRequest request) {
        if (currentUserId != null) {
            return currentUserId;
        }
        if (request != null) {
            Object attrUserId = request.getAttribute("currentUserId");
            if (attrUserId instanceof Number) {
                return ((Number) attrUserId).longValue();
            }
            if (attrUserId instanceof String && StringUtils.hasText((String) attrUserId)) {
                try {
                    return Long.valueOf((String) attrUserId);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String bearerPrefix = "Bearer ";
        if (!authorization.startsWith(bearerPrefix)) {
            return null;
        }
        String token = authorization.substring(bearerPrefix.length()).trim();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return authService.parseUserIdFromAccessToken(token);
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : defaultValue;
    }

    /**
     * 注册设备 SSID（用于测试）
     */
    @Operation(summary = "注册设备 SSID", description = "在服务端登记设备SSID（测试用途）")
    @PostMapping("/device/{deviceId}/register-ssid")
    public ResponseEntity<Map<String, Object>> registerSsid(
            @PathVariable String deviceId,
            @RequestParam String ssid) {

        log.info("注册设备 {} 的SSID", deviceId);

        mqttMessageService.registerDeviceSsid(deviceId, ssid);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "SSID 已注册");
        return ResponseEntity.ok(result);
    }
}
