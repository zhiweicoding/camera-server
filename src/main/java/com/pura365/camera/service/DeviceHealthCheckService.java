package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.Device;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备健康检查服务
 * 
 * 注意：定时检测已禁用，设备在线状态由以下机制维护：
 * - CODE10: 设备上线
 * - CODE11: 设备列表/手动刷新时发送探测，3秒无响应标记离线
 * - CODE20: 设备遗言消息，立即标记离线
 */
@Service
public class DeviceHealthCheckService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthCheckService.class);

    /** 应用是否正在关闭 */
    private volatile boolean shuttingDown = false;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MqttMessageService mqttMessageService;

    /** 心跳检测间隔(毫秒)，默认60秒 */
    @Value("${device.health.check-interval:60000}")
    private long checkInterval;

    /** CODE11响应超时时间(毫秒)，默认3秒 */
    @Value("${device.health.response-timeout:3000}")
    private long responseTimeoutMs;

    /** 是否启用健康检查（默认禁用） */
    @Value("${device.health.enabled:false}")
    private boolean healthCheckEnabled;

    /**
     * 定时执行设备健康检查
     * 每60秒执行一次，向在线设备发送CODE11探测
     */
    @Scheduled(fixedDelayString = "${device.health.check-interval:60000}")
    public void checkDeviceHealth() {
        if (!healthCheckEnabled || shuttingDown) {
            return;
        }

        log.info("开始执行设备健康检查...");

        try {
            // 向所有在线设备发送CODE11探测，超时则标记离线
            sendHeartbeatToOnlineDevices();
        } catch (Exception e) {
            log.error("设备健康检查执行失败", e);
        }
    }

    /**
     * 向所有在线设备发送CODE11探测
     * 如果3秒内没有响应则标记为离线
     */
    private void sendHeartbeatToOnlineDevices() {
        // 查询所有在线设备
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getStatus, DeviceOnlineStatus.ONLINE)
                .isNotNull(Device::getSsid)
                .ne(Device::getSsid, "");

        List<Device> onlineDevices = deviceRepository.selectList(queryWrapper);

        if (onlineDevices.isEmpty()) {
            log.info("当前没有在线设备，跳过心跳检测");
            return;
        }

        log.info("开始向 {} 个在线设备发送CODE11探测", onlineDevices.size());

        for (Device device : onlineDevices) {
            try {
                // 发送CODE11并等待响应，超时则标记离线
                boolean responded = mqttMessageService.requestDeviceInfoAndWait(device.getId(), responseTimeoutMs);
                if (!responded) {
                    log.info("设备 {} CODE11响应超时({}ms)，标记为离线", device.getId(), responseTimeoutMs);
                    mqttMessageService.markDeviceOffline(device.getId());
                }
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

    @Override
    public void destroy() {
        log.info("应用关闭中，停止设备健康检查任务");
        shuttingDown = true;
    }
}
