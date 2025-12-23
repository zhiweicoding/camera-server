package com.pura365.camera.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.service.MqttMessageService;
import com.pura365.camera.util.TimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    private MqttMessageService mqttMessageService;

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
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 26);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            msg.put("enable", enable);

            mqttMessageService.sendToDevice(deviceId, msg, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送旋转设置");
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
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 28);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            msg.put("enable", enable);

            mqttMessageService.sendToDevice(deviceId, msg, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送白光灯设置");
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
            @RequestParam String rtcServer) { // 格式: server,user,pass

        log.info("请求设备 {} 的 WebRTC Offer - SID: {}", deviceId, sid);

        try {
            mqttMessageService.requestWebRtcOffer(deviceId, sid, rtcServer);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已请求 WebRTC Offer");
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
     * 设置灯泡参数（CODE 29）
     */
    @Operation(summary = "设置灯泡参数", description = "配置灯泡工作模式、亮度、定时等参数")
    @PostMapping("/device/{deviceId}/bulb/config")
    public ResponseEntity<Map<String, Object>> setBulbConfig(
            @PathVariable String deviceId,
            @RequestParam Integer detect, // 0:手动 1:环境光自动 2:定时
            @RequestParam(required = false) Integer brightness, // 亮度 1-100
            @RequestParam(required = false) Integer enable, // 手动模式: 0关闭 1开启
            @RequestParam(required = false) String timeOn1, // 定时一开启时间 hh:mm
            @RequestParam(required = false) String timeOff1, // 定时一关闭时间 hh:mm
            @RequestParam(required = false) String timeOn2, // 定时二开启时间 hh:mm
            @RequestParam(required = false) String timeOff2) { // 定时二关闭时间 hh:mm

        log.info("设置设备 {} 灯泡参数: detect={}", deviceId, detect);

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 29);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            msg.put("detect", detect);

            if (brightness != null) {
                msg.put("brightness", brightness);
            }
            if (enable != null) {
                msg.put("enable", enable);
            }
            if (timeOn1 != null) {
                msg.put("time_on1", timeOn1);
            }
            if (timeOff1 != null) {
                msg.put("time_off1", timeOff1);
            }
            if (timeOn2 != null) {
                msg.put("time_o2", timeOn2);
            }
            if (timeOff2 != null) {
                msg.put("time_off2", timeOff2);
            }

            mqttMessageService.sendToDevice(deviceId, msg, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送灯泡配置");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("设置灯泡参数失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取灯泡配置参数（CODE 30）
     */
    @Operation(summary = "获取灯泡配置", description = "获取灯泡当前的配置参数")
    @PostMapping("/device/{deviceId}/bulb/info")
    public ResponseEntity<Map<String, Object>> getBulbConfig(
            @PathVariable String deviceId) {

        log.info("获取设备 {} 灯泡配置", deviceId);

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 30);
            msg.put("time", TimeValidator.getCurrentTimestamp());

            mqttMessageService.sendToDevice(deviceId, msg, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送获取灯泡配置请求");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取灯泡配置失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 注册设备 SSID（用于测试）
     */
    @Operation(summary = "注册设备 SSID", description = "在服务器侧记录设备 SSID（测试用途）")
    @PostMapping("/device/{deviceId}/register-ssid")
    public ResponseEntity<Map<String, Object>> registerSsid(
            @PathVariable String deviceId,
            @RequestParam String ssid) {

        log.info("注册设备 {} 的 SSID", deviceId);

        mqttMessageService.registerDeviceSsid(deviceId, ssid);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "SSID 已注册");
        return ResponseEntity.ok(result);
    }
}
