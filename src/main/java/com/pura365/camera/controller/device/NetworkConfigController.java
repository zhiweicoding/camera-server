package com.pura365.camera.controller.device;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.Device;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.domain.NetworkConfig;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.model.NetworkConfigRequest;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.NetworkConfigRepository;
import com.pura365.camera.service.DeviceSsidService;
import com.pura365.camera.service.NetworkPairingStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "网络配置", description = "设备网络配置相关接口")
@RestController
@RequestMapping("/api/device/network")
public class NetworkConfigController {

    private static final Logger log = LoggerFactory.getLogger(NetworkConfigController.class);

    @Autowired
    private NetworkConfigRepository networkConfigRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceSsidService deviceSsidService;

    @Autowired
    private NetworkPairingStatusService pairingStatusService;

    /**
     * 提交配网信息
     * APP 在完成设备配网后调用此接口，保存配网信息到数据库
     */
    @Operation(summary = "提交配网信息", description = "APP 完成设备配网后提交配网结果")
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> submitNetworkConfig(
            @Valid @RequestBody NetworkConfigRequest request) {

        log.info("收到配网信息 - 设备: {}, SSID: {}", request.getDeviceId(), request.getSsid());

        try {
            // 1. 创建或更新设备记录（根据主键 deviceId）
            Device device = deviceRepository.selectById(request.getDeviceId());
            if (device == null) {
                device = new Device();
                device.setId(request.getDeviceId());
            }
            device.setSsid(request.getSsid());
            device.setRegion(request.getRegion());
            device.setEnabled(EnableStatus.ENABLED);

            if (deviceRepository.selectById(device.getId()) == null) {
                deviceRepository.insert(device);
            } else {
                deviceRepository.updateById(device);
            }

            // 2. 保存配网信息
            NetworkConfig config = new NetworkConfig();
            config.setDeviceId(request.getDeviceId());
            config.setSsid(request.getSsid());
            config.setPassword(request.getPassword()); // TODO: 加密存储
            config.setTimezone(request.getTimezone());
            config.setRegion(request.getRegion());
            config.setConfigMethod(request.getConfigMethod());
            config.setConfigSource(request.getConfigSource());
            config.setConfigStatus(0); // 0-配网中 1-成功 2-失败
            networkConfigRepository.insert(config);

            // 3. 保存 SSID 到缓存，便于后续使用
            deviceSsidService.saveSsid(request.getDeviceId(), request.getSsid());

            // 4. 设置配网状态为"配网中"（存储到Redis）
            pairingStatusService.setPairing(request.getDeviceId());

            log.info("配网信息已保存 - 设备: {}", request.getDeviceId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配网信息已保存");
            result.put("deviceId", request.getDeviceId());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("保存配网信息失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 更新配网状态
     * 设备配网成功后调用，更新状态为成功或失败
     */
    @Operation(summary = "更新配网状态", description = "根据设备 ID 更新最近一次配网记录的状态")
    @PostMapping("/config/{deviceId}/status")
    public ResponseEntity<Map<String, Object>> updateConfigStatus(
            @PathVariable String deviceId,
            @RequestParam Integer status) { // 0-配网中 1-成功 2-失败

        log.info("更新配网状态 - 设备: {}, 状态: {}", deviceId, status);

        try {
            // 查找最新的配网记录（按 created_at 排序，取最新一条）
            QueryWrapper<NetworkConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("device_id", deviceId).orderByDesc("created_at").last("LIMIT 1");
            NetworkConfig config = networkConfigRepository.selectOne(wrapper);
            if (config == null) {
                throw new RuntimeException("配网记录不存在");
            }

            config.setConfigStatus(status);
            networkConfigRepository.updateById(config);

            // 如果配网成功，更新设备状态
            if (status == 1) {
                Device device = deviceRepository.selectById(deviceId);
                if (device != null) {
                    device.setStatus(DeviceOnlineStatus.ONLINE);
                    deviceRepository.updateById(device);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配网状态已更新");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("更新配网状态失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 查询设备配网历史
     */
    @Operation(summary = "查询配网历史", description = "查询设备的所有配网记录")
    @GetMapping("/config/{deviceId}/history")
    public ResponseEntity<?> getConfigHistory(@PathVariable String deviceId) {
        try {
            QueryWrapper<NetworkConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("device_id", deviceId).orderByDesc("created_at");
            List<NetworkConfig> list = networkConfigRepository.selectList(wrapper);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("查询配网历史失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 查询设备配网/绑定状态
     * APP在配网过程中轮询此接口，判断配网是否成功
     */
    @Operation(summary = "查询配网状态", description = "APP轮询此接口判断设备配网是否成功")
    @GetMapping("/config/{deviceId}/status")
    public ApiResponse<Map<String, Object>> getPairingStatus(@PathVariable String deviceId) {
        log.info("查询配网状态 - 设备: {}", deviceId);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);

        String status = pairingStatusService.getStatus(deviceId);
        
        if (status == null) {
            // 无状态，可能是过期或未提交配网信息
            result.put("status", "UNKNOWN");
            result.put("success", false);
            result.put("message", "未找到配网记录或已过期");
        } else if (NetworkPairingStatusService.STATUS_PAIRING.equals(status)) {
            // 配网中
            result.put("status", "PAIRING");
            result.put("success", false);
            result.put("message", "配网中，请稍候...");
        } else if (NetworkPairingStatusService.STATUS_SUCCESS.equals(status)) {
            // 配网成功
            result.put("status", "SUCCESS");
            result.put("success", true);
            result.put("message", "配网成功");
        } else if (NetworkPairingStatusService.STATUS_FAILED.equals(status)) {
            // 配网失败
            result.put("status", "FAILED");
            result.put("success", false);
            result.put("message", "配网失败");
        } else {
            result.put("status", status);
            result.put("success", false);
            result.put("message", "未知状态");
        }
        log.info("配网状态查询结果 - 设备: {}, 状态: {}", deviceId, result.get("status"));
        return ApiResponse.success(result);
    }
}
