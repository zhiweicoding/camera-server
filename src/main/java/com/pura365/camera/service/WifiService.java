package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.DeviceBinding;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.domain.WifiHistory;
import com.pura365.camera.enums.DeviceBindingStatus;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.enums.UserDeviceRole;
import com.pura365.camera.model.wifi.BindDeviceRequest;
import com.pura365.camera.model.wifi.BindingStatusVO;
import com.pura365.camera.model.wifi.WifiInfoVO;
import com.pura365.camera.repository.DeviceBindingRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.repository.WifiHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WiFi配网与设备绑定服务
 */
@Service
@RequiredArgsConstructor
public class WifiService {

    private static final Logger log = LoggerFactory.getLogger(WifiService.class);

    private final WifiHistoryRepository wifiHistoryRepository;
    private final DeviceRepository deviceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final DeviceBindingRepository deviceBindingRepository;

    /**
     * 获取用户WiFi历史记录
     *
     * @param userId 用户ID
     * @return WiFi列表
     */
    public List<WifiInfoVO> getWifiHistory(Long userId) {
        log.info("查询用户WiFi历史记录, userId={}", userId);
        
        QueryWrapper<WifiHistory> qw = new QueryWrapper<>();
        qw.lambda().eq(WifiHistory::getUserId, userId)
                .orderByDesc(WifiHistory::getLastUsedAt)
                .last("limit 20");
        
        List<WifiHistory> list = wifiHistoryRepository.selectList(qw);
        
        List<WifiInfoVO> result = list.stream()
                .map(this::convertToWifiInfoVO)
                .collect(Collectors.toList());
        
        log.info("查询到WiFi记录{}条, userId={}", result.size(), userId);
        return result;
    }

    /**
     * 绑定设备
     *
     * @param userId  用户ID
     * @param request 绑定请求
     * @return 绑定结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BindingStatusVO bindDevice(Long userId, BindDeviceRequest request) {
        String deviceSn = request.getDeviceSn();
        log.info("开始设备绑定流程, userId={}, deviceSn={}, wifiSsid={}", 
                userId, deviceSn, request.getWifiSsid());
        
        // 1. 创建或更新设备
        Device device = createOrUpdateDevice(deviceSn, request);
        
        // 2. 创建绑定关系
        createUserDeviceBinding(userId, deviceSn);
        
        // 3. 创建绑定记录
        DeviceBinding binding = createDeviceBinding(userId, deviceSn, request);
        
        // 4. 保存WiFi历史
        saveWifiHistory(userId, request.getWifiSsid());
        
        log.info("设备绑定流程完成, userId={}, deviceSn={}, bindingId={}", 
                userId, deviceSn, binding.getId());
        
        return BindingStatusVO.builder()
                .deviceId(device.getId())
                .deviceName(device.getName())
                .status(DeviceBindingStatus.BINDING.getCode())
                .progress(0)
                .message("正在配置WiFi")
                .build();
    }

    /**
     * 查询绑定状态
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 绑定状态
     */
    public BindingStatusVO getBindingStatus(Long userId, String deviceId) {
        log.debug("查询绑定状态, userId={}, deviceId={}", userId, deviceId);
        
        QueryWrapper<DeviceBinding> qw = new QueryWrapper<>();
        qw.lambda().eq(DeviceBinding::getUserId, userId)
                .eq(DeviceBinding::getDeviceId, deviceId)
                .orderByDesc(DeviceBinding::getCreatedAt)
                .last("limit 1");
        
        DeviceBinding binding = deviceBindingRepository.selectOne(qw);
        
        if (binding == null) {
            log.warn("未找到绑定记录, userId={}, deviceId={}", userId, deviceId);
            return BindingStatusVO.builder()
                    .deviceId(deviceId)
                    .status(DeviceBindingStatus.BINDING.getCode())
                    .progress(0)
                    .message("未找到绑定记录")
                    .build();
        }
        
        log.debug("查询到绑定状态, deviceId={}, status={}, progress={}", 
                deviceId, binding.getStatus(), binding.getProgress());
        
        return BindingStatusVO.builder()
                .deviceId(deviceId)
                .status(binding.getStatus() != null ? binding.getStatus().getCode() : null)
                .progress(binding.getProgress())
                .message(binding.getMessage())
                .build();
    }

    // ==================== 私有方法 ====================

    private Device createOrUpdateDevice(String deviceSn, BindDeviceRequest request) {
        Device device = deviceRepository.selectById(deviceSn);
        LocalDateTime now = LocalDateTime.now();
        if (device == null) {
            log.info("设备不存在，创建新设备, deviceSn={}", deviceSn);
            device = new Device();
            device.setId(deviceSn);
            device.setName(request.getDeviceName());
            device.setSsid(request.getWifiSsid());
            device.setStatus(DeviceOnlineStatus.ONLINE);  // 新设备初始为在线
            device.setEnabled(EnableStatus.ENABLED);
            device.setLastOnlineTime(now);
            device.setLastHeartbeatTime(now);
            device.setCreatedAt(LocalDateTime.now());
            device.setUpdatedAt(LocalDateTime.now());
            deviceRepository.insert(device);
            log.info("新设备创建成功(初始离线状态), deviceSn={}", deviceSn);
        } else {
            log.info("设备已存在，更新设备信息, deviceSn={}", deviceSn);
            boolean needUpdate = false;
            
            if (request.getDeviceName() != null && !request.getDeviceName().isEmpty()) {
                device.setName(request.getDeviceName());
                needUpdate = true;
            }
            if (request.getWifiSsid() != null && !request.getWifiSsid().isEmpty()) {
                device.setSsid(request.getWifiSsid());
                needUpdate = true;
            }
            device.setLastOnlineTime(now);
            device.setLastHeartbeatTime(now);
            device.setStatus(DeviceOnlineStatus.ONLINE);  // 新设备初始为在线
            device.setEnabled(EnableStatus.ENABLED);
            if (needUpdate) {
                device.setUpdatedAt(LocalDateTime.now());
                deviceRepository.updateById(device);
                log.info("设备信息更新成功, deviceSn={}", deviceSn);
            }
        }
        
        return device;
    }

    private void createUserDeviceBinding(Long userId, String deviceSn) {
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceSn);
        
        if (userDeviceRepository.selectCount(qw) == 0) {
            log.info("创建用户设备绑定关系, userId={}, deviceSn={}", userId, deviceSn);
            UserDevice ud = new UserDevice();
            ud.setUserId(userId);
            ud.setDeviceId(deviceSn);
            ud.setRole(UserDeviceRole.OWNER);
            userDeviceRepository.insert(ud);
        } else {
            log.debug("用户设备绑定关系已存在, userId={}, deviceSn={}", userId, deviceSn);
        }
    }

    private DeviceBinding createDeviceBinding(Long userId, String deviceSn, BindDeviceRequest request) {
        DeviceBinding binding = new DeviceBinding();
        binding.setDeviceId(deviceSn);
        binding.setDeviceSn(deviceSn);
        binding.setUserId(userId);
        binding.setWifiSsid(request.getWifiSsid());
        binding.setWifiPassword(request.getWifiPassword());
        binding.setStatus(DeviceBindingStatus.BINDING);
        binding.setProgress(0);
        binding.setMessage("正在配置WiFi");
        deviceBindingRepository.insert(binding);
        
        log.info("绑定记录创建成功, userId={}, deviceSn={}, bindingId={}", 
                userId, deviceSn, binding.getId());
        return binding;
    }

    private void saveWifiHistory(Long userId, String wifiSsid) {
        if (wifiSsid == null || wifiSsid.isEmpty()) {
            return;
        }
        
        log.debug("保存WiFi历史记录, userId={}, ssid={}", userId, wifiSsid);
        WifiHistory history = new WifiHistory();
        history.setUserId(userId);
        history.setSsid(wifiSsid);
        history.setSignal(null);
        history.setSecurity("WPA2");
        history.setIsConnected(1);  // 刚绑定应该是连接状态
        history.setLastUsedAt(new Date());
        wifiHistoryRepository.insert(history);
    }

    private WifiInfoVO convertToWifiInfoVO(WifiHistory history) {
        return WifiInfoVO.builder()
                .ssid(history.getSsid())
                .signal(history.getSignal())
                .security(history.getSecurity())
                .isConnected(history.getIsConnected() != null && history.getIsConnected() == 1)
                .build();
    }
}
