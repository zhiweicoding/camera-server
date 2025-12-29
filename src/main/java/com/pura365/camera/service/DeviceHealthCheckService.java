package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pura365.camera.domain.Device;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备健康检查服务
 * 定时向设备发送心跳检测，更新设备在线状态
 * 
 * 心跳机制说明：
 * 1. 设备初始状态为离线(status=0)
 * 2. 只有当设备通过MQTT发送有效消息(CODE 138/139)时才会被标记为在线
 * 3. 定时任务会检查所有在线设备的心跳时间，超时则标记为离线
 * 4. 从未收到过有效心跳的设备（lastHeartbeatTime为null）会被直接标记为离线
 */
@Service
public class DeviceHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthCheckService.class);

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MqttMessageService mqttMessageService;

    /** 心跳检测间隔(毫秒)，默认60秒 */
    @Value("${device.health.check-interval:60000}")
    private long checkInterval;

    /** 离线判定阈值(秒)，默认180秒(3分钟) */
    @Value("${device.health.offline-threshold:180}")
    private int offlineThresholdSeconds;

    /** 是否启用健康检查 */
    @Value("${device.health.enabled:true}")
    private boolean healthCheckEnabled;

    /**
     * 定时执行设备健康检查
     * 每60秒执行一次
     */
    @Scheduled(fixedDelayString = "${device.health.check-interval:60000}")
    public void checkDeviceHealth() {
        if (!healthCheckEnabled) {
            return;
        }

        log.info("开始执行设备健康检查...");

        try {
            // 1. 检查超时未响应的设备，标记为离线
            markOfflineDevices();
            
            // 2. 标记从未有过心跳的在线设备为离线（防止虚假设备）
            markNeverHeartbeatDevicesOffline();

            // 3. 向所有在线设备发送心跳请求
            sendHeartbeatToOnlineDevices();

        } catch (Exception e) {
            log.error("设备健康检查执行失败", e);
        }
    }

    /**
     * 检查并标记离线设备
     * 判断标准：最后心跳时间超过阈值的在线设备
     */
    private void markOfflineDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);

        // 查找需要标记为离线的设备：
        // 条件1: 状态为在线(1)
        // 条件2: 最后心跳时间不为空且早于阈值
        LambdaUpdateWrapper<Device> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Device::getStatus, DeviceOnlineStatus.ONLINE)
                .isNotNull(Device::getLastHeartbeatTime) // 有过心跳记录
                .lt(Device::getLastHeartbeatTime, threshold) // 心跳超时
                .set(Device::getStatus, DeviceOnlineStatus.OFFLINE)
                .set(Device::getUpdatedAt, LocalDateTime.now());

        int updatedCount = deviceRepository.update(null, updateWrapper);
        if (updatedCount > 0) {
            log.info("已将 {} 个设备标记为离线(心跳超时 {}秒)", updatedCount, offlineThresholdSeconds);
        }
    }
    
    /**
     * 标记从未有过心跳记录的在线设备为离线
     * 这种情况通常是用户绑定了不存在的设备ID，或设备从未真正连接过
     */
    private void markNeverHeartbeatDevicesOffline() {
        // 查找在线但从未有心跳的设备
        LambdaUpdateWrapper<Device> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Device::getStatus, DeviceOnlineStatus.ONLINE)
                .isNull(Device::getLastHeartbeatTime) // 从未有心跳记录
                .set(Device::getStatus, DeviceOnlineStatus.OFFLINE)
                .set(Device::getUpdatedAt, LocalDateTime.now());

        int updatedCount = deviceRepository.update(null, updateWrapper);
        if (updatedCount > 0) {
            log.info("已将 {} 个从未心跳的设备标记为离线", updatedCount);
        }
    }

    /**
     * 向所有在线设备发送心跳请求(CODE 11)
     */
    private void sendHeartbeatToOnlineDevices() {
        // 查询所有在线设备（必须有过心跳记录才发送，避免向虚假设备发送）
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getStatus, DeviceOnlineStatus.ONLINE)
                .eq(Device::getEnabled, EnableStatus.ENABLED)
                .isNotNull(Device::getLastHeartbeatTime); // 有过心跳记录

        List<Device> onlineDevices = deviceRepository.selectList(queryWrapper);

        if (onlineDevices.isEmpty()) {
            log.info("当前没有在线设备，跳过心跳检测");
            return;
        }

        log.info("开始向 {} 个在线设备发送心跳请求", onlineDevices.size());

        for (Device device : onlineDevices) {
            try {
                mqttMessageService.requestDeviceInfo(device.getId());
                log.info("已向设备 {} 发送心跳请求", device.getId());
            } catch (Exception e) {
                log.warn("向设备 {} 发送心跳请求失败: {}", device.getId(), e.getMessage());
            }
        }
    }

    /**
     * 手动触发单个设备的心跳检测
     */
    public void checkSingleDevice(String deviceId) {
        try {
            mqttMessageService.requestDeviceInfo(deviceId);
            log.info("已向设备 {} 发送心跳请求", deviceId);
        } catch (Exception e) {
            log.error("向设备 {} 发送心跳请求失败", deviceId, e);
        }
    }

    /**
     * 获取设备在线状态统计
     */
    public DeviceStatusStats getDeviceStatusStats() {
        LambdaQueryWrapper<Device> onlineWrapper = new LambdaQueryWrapper<>();
        onlineWrapper.eq(Device::getStatus, DeviceOnlineStatus.ONLINE);
        long onlineCount = deviceRepository.selectCount(onlineWrapper);

        LambdaQueryWrapper<Device> offlineWrapper = new LambdaQueryWrapper<>();
        offlineWrapper.eq(Device::getStatus, DeviceOnlineStatus.OFFLINE);
        long offlineCount = deviceRepository.selectCount(offlineWrapper);

        long totalCount = deviceRepository.selectCount(null);

        return new DeviceStatusStats(totalCount, onlineCount, offlineCount);
    }

    /**
     * 设备状态统计
     */
    public static class DeviceStatusStats {
        private final long total;
        private final long online;
        private final long offline;

        public DeviceStatusStats(long total, long online, long offline) {
            this.total = total;
            this.online = online;
            this.offline = offline;
        }

        public long getTotal() { return total; }
        public long getOnline() { return online; }
        public long getOffline() { return offline; }
    }
}
