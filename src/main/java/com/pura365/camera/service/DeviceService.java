package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.model.device.*;
import com.pura365.camera.repository.*;
import com.pura365.camera.util.TimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备管理服务
 * <p>
 * 提供设备增删改查、设备设置、本地录像查询、云台控制等业务逻辑
 *
 * @author camera-server
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    /**
     * 设备状态：在线
     */
    private static final int DEVICE_STATUS_ONLINE = 1;

    /**
     * 设备状态：离线
     */
    private static final int DEVICE_STATUS_OFFLINE = 0;

    /**
     * 设备启用状态：启用
     */
    private static final int DEVICE_ENABLED = 1;

    /**
     * 用户设备角色：所有者
     */
    private static final String USER_DEVICE_ROLE_OWNER = "owner";

    /**
     * 绑定状态：成功
     */
    private static final String BINDING_STATUS_SUCCESS = "success";

    /**
     * 绑定进度：完成
     */
    private static final int BINDING_PROGRESS_COMPLETE = 100;

    /**
     * 绑定成功消息
     */
    private static final String BINDING_SUCCESS_MESSAGE = "绑定成功";

    /**
     * 默认每页数量
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * MQTT PTZ 控制指令码
     */
    private static final int MQTT_CODE_PTZ = 99;

    /**
     * ISO8601 时间格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private LocalVideoRepository localVideoRepository;

    @Autowired
    private DeviceBindingRepository deviceBindingRepository;

    @Autowired
    private MqttMessageService mqttMessageService;

    // ==================== 设备查询 ====================

    /**
     * 获取用户的设备列表
     *
     * @param userId 用户ID
     * @return 设备列表
     */
    public List<DeviceListItemVO> listDevices(Long userId) {
        // 查询用户绑定的设备
        LambdaQueryWrapper<UserDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDevice::getUserId, userId);
        List<UserDevice> bindings = userDeviceRepository.selectList(queryWrapper);

        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建设备列表响应
        return bindings.stream()
                .map(ud -> deviceRepository.selectById(ud.getDeviceId()))
                .filter(Objects::nonNull)
                .map(device -> buildDeviceListItem(userId, device))
                .collect(Collectors.toList());
    }

    /**
     * 获取设备详情
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 设备详情，不存在返回null
     */
    public DeviceDetailVO getDeviceDetail(Long userId, String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return null;
        }
        return buildDeviceDetail(userId, device);
    }

    // ==================== 设备管理 ====================

    /**
     * 添加设备
     *
     * @param userId  用户ID
     * @param request 添加设备请求
     * @return 设备基础信息
     */
    public DeviceVO addDevice(Long userId, AddDeviceRequest request) {
        String deviceId = request.getDeviceId();

        // 创建或更新设备
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            device = createNewDevice(request);
            deviceRepository.insert(device);
            log.info("创建新设备: {}", deviceId);
        } else {
            updateDeviceIfNeeded(device, request);
        }

        // 绑定用户与设备
        bindUserDevice(userId, deviceId);

        // 记录绑定信息
        saveDeviceBinding(userId, request);

        // 构建响应
        return buildDeviceVO(device);
    }

    /**
     * 删除/解绑设备
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     */
    public void deleteDevice(Long userId, String deviceId) {
        LambdaQueryWrapper<UserDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        userDeviceRepository.delete(queryWrapper);
        log.info("用户 {} 解绑设备 {}", userId, deviceId);
    }

    /**
     * 更新设备信息
     *
     * @param deviceId 设备ID
     * @param request  更新请求
     * @return 更新后的设备信息，设备不存在返回null
     */
    public DeviceVO updateDevice(String deviceId, UpdateDeviceRequest request) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return null;
        }

        // 目前只支持修改名称
        if (StringUtils.hasText(request.getName())) {
            device.setName(request.getName());
            deviceRepository.updateById(device);
            log.info("更新设备 {} 名称为: {}", deviceId, request.getName());
        }
        if (request.getAiEnabled() != null) {
            device.setAiEnabled(request.getAiEnabled());
            deviceRepository.updateById(device);
            log.info("更新设备 {} 名称为: {}", deviceId, request.getName());
        }

        return buildDeviceVO(device);
    }

    // ==================== 本地录像 ====================

    /**
     * 获取本地录像列表（分页）
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param date     日期过滤（可选，格式：yyyy-MM-dd）
     * @param page     页码（从1开始）
     * @param pageSize 每页数量
     * @return 分页结果，无权限返回null
     */
    public LocalVideoPageVO listLocalVideos(Long userId, String deviceId, String date, int page, int pageSize) {
        // 检查用户权限
        if (!hasUserDevice(userId, deviceId)) {
            return null;
        }

        // 参数校正
        if (page < 1) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        int offset = (page - 1) * pageSize;

        // 构建查询条件
        QueryWrapper<LocalVideo> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(LocalVideo::getDeviceId, deviceId);
        if (StringUtils.hasText(date)) {
            queryWrapper.apply("DATE(created_at) = {0}", date);
        }
        queryWrapper.orderByDesc("created_at");

        // 查询总数
        int total = localVideoRepository.selectCount(queryWrapper).intValue();

        // 查询分页数据
        List<LocalVideo> rows = localVideoRepository.selectList(
                queryWrapper.last("LIMIT " + offset + "," + pageSize));

        // 构建响应
        LocalVideoPageVO result = new LocalVideoPageVO();
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setList(rows == null ? Collections.emptyList() :
                rows.stream().map(this::buildLocalVideoVO).collect(Collectors.toList()));

        return result;
    }

    // ==================== 云台控制 ====================

    /**
     * 发送云台控制指令
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param request  控制请求
     * @return 是否有权限执行（null表示无权限）
     * @throws Exception MQTT发送失败
     */
    public Boolean sendPtzCommand(Long userId, String deviceId, PtzControlRequest request) throws Exception {
        // 检查用户权限
        if (!hasUserDevice(userId, deviceId)) {
            return null;
        }

        // 构建MQTT消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", MQTT_CODE_PTZ);
        msg.put("time", TimeValidator.getCurrentTimestamp());
        msg.put("command", request.getDirection());

        // 发送到设备
        mqttMessageService.sendToDevice(deviceId, msg, null);
        log.info("发送PTZ指令到设备 {}: {}", deviceId, request.getDirection());

        return true;
    }

    // ==================== 权限检查 ====================

    /**
     * 检查用户是否拥有指定设备
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 是否拥有
     */
    public boolean hasUserDevice(Long userId, String deviceId) {
        LambdaQueryWrapper<UserDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        Long count = userDeviceRepository.selectCount(queryWrapper);
        return count != null && count > 0;
    }

    /**
     * 检查设备是否存在
     *
     * @param deviceId 设备ID
     * @return 是否存在
     */
    public boolean deviceExists(String deviceId) {
        return deviceRepository.selectById(deviceId) != null;
    }

    // ==================== 私有方法 ====================

    /**
     * 构建设备列表项VO
     */
    private DeviceListItemVO buildDeviceListItem(Long userId, Device device) {
        DeviceListItemVO vo = new DeviceListItemVO();
        vo.setId(device.getId());
        vo.setName(device.getName());
        vo.setModel(null); // 暂无型号字段
        vo.setStatus(isOnline(device) ? "online" : "offline");
        // 云存储状态
//        CloudSubscription subscription = findActiveSubscription(userId, device.getId());
//        boolean hasCloud = subscription != null &&
//                (subscription.getExpireAt() == null || subscription.getExpireAt().after(new Date()));
//        vo.setHasCloudStorage(hasCloud);
//        vo.setCloudExpireAt(hasCloud && subscription.getExpireAt() != null ?
//                formatIsoTime(subscription.getExpireAt()) : null);
        // 云存储开关
        vo.setHasCloudStorage(device.getCloudStorage() == 0); // 兼容旧数据
        // AI功能开关
        vo.setAiEnabled(device.getAiEnabled() != null && device.getAiEnabled() == 1);
        // 信号强度
        vo.setWifiRssi(device.getWifiRssi());
        // 网络类型
        vo.setNetworkType(device.getNetworkType());
        vo.setThumbnailUrl(null); // 暂无缩略图字段
        vo.setLastOnlineAt(device.getLastOnlineTime() != null ?
                device.getLastOnlineTime().toString() : null);

        return vo;
    }

    /**
     * 构建设备详情VO
     */
    private DeviceDetailVO buildDeviceDetail(Long userId, Device device) {
        DeviceDetailVO vo = new DeviceDetailVO();
        vo.setId(device.getId());
        vo.setName(device.getName());
        vo.setModel(null); // 暂无型号字段
        vo.setMac(device.getMac());
        vo.setFirmwareVersion(device.getFirmwareVersion());
        vo.setStatus(isOnline(device) ? "online" : "offline");

        // 云存储状态
        CloudSubscription subscription = findActiveSubscription(userId, device.getId());
        boolean hasCloud = subscription != null &&
                (subscription.getExpireAt() == null || subscription.getExpireAt().after(new Date()));
        vo.setHasCloudStorage(hasCloud);
        vo.setCloudExpireAt(hasCloud && subscription.getExpireAt() != null ?
                formatIsoTime(subscription.getExpireAt()) : null);

        vo.setThumbnailUrl(null);
        vo.setWifiSsid(device.getSsid());
        vo.setWifiRssi(device.getWifiRssi());

        // SD卡信息
        SdCardInfoVO sdCardInfo = buildSdCardInfo(device);
        vo.setSdCard(sdCardInfo);

        // 设备设置字段直接放在详情里
        vo.setRotate(device.getRotate());
        vo.setLightLed(device.getLightLed());
        vo.setWhiteLed(device.getWhiteLed());

        vo.setLastOnlineAt(device.getLastOnlineTime() != null ?
                device.getLastOnlineTime().toString() : null);
        vo.setLastHeartbeatAt(device.getLastHeartbeatTime() != null ?
                device.getLastHeartbeatTime().toString() : null);
        vo.setFreeCloudClaimed(device.getFreeCloudClaimed() != null && device.getFreeCloudClaimed() == 1);

        return vo;
    }

    /**
     * 构建SD卡信息VO
     */
    private SdCardInfoVO buildSdCardInfo(Device device) {
        SdCardInfoVO sdCardInfo = new SdCardInfoVO();
        sdCardInfo.setState(device.getSdState() != null ? device.getSdState() : 0);

        // 计算SD卡容量（字节）
        if (device.getSdCapacity() != null && device.getSdBlockSize() != null) {
            long total = device.getSdCapacity() * device.getSdBlockSize();
            long available = device.getSdFree() != null ?
                    device.getSdFree() * device.getSdBlockSize() : 0L;
            long used = total - available;

            sdCardInfo.setTotal(total);
            sdCardInfo.setUsed(used);
            sdCardInfo.setAvailable(available);
        }

        return sdCardInfo;
    }

    /**
     * 构建设备基础信息VO
     */
    private DeviceVO buildDeviceVO(Device device) {
        DeviceVO vo = new DeviceVO();
        vo.setId(device.getId());
        vo.setName(device.getName());
        vo.setModel(null);
        vo.setStatus(isOnline(device) ? "online" : "offline");
        return vo;
    }

    /**
     * 构建本地录像VO
     */
    private LocalVideoVO buildLocalVideoVO(LocalVideo video) {
        LocalVideoVO vo = new LocalVideoVO();
        vo.setId(video.getVideoId());
        vo.setDeviceId(video.getDeviceId());
        vo.setType(video.getType());
        vo.setTitle(video.getTitle());
        vo.setThumbnailUrl(video.getThumbnail());
        vo.setVideoUrl(video.getVideoUrl());
        vo.setDuration(video.getDuration());
        vo.setSize(video.getSize());
        vo.setCreatedAt(video.getCreatedAt() != null ? formatIsoTime(video.getCreatedAt()) : null);
        return vo;
    }

    /**
     * 创建新设备实体
     */
    private Device createNewDevice(AddDeviceRequest request) {
        Device device = new Device();
        device.setId(request.getDeviceId());
        device.setName(request.getName());
        device.setSsid(request.getWifiSsid());
        device.setMac(request.getMac());
        device.setNetworkType("wifi");
        device.setStatus(DEVICE_STATUS_OFFLINE);
        device.setEnabled(DEVICE_ENABLED);
        return device;
    }

    /**
     * 更新设备信息（如果需要）
     */
    private void updateDeviceIfNeeded(Device device, AddDeviceRequest request) {
        if (StringUtils.hasText(request.getName())) {
            device.setName(request.getName());
            deviceRepository.updateById(device);
            log.info("更新设备 {} 名称为: {}", device.getId(), request.getName());
        }
    }

    /**
     * 绑定用户与设备（如果未绑定）
     */
    private void bindUserDevice(Long userId, String deviceId) {
        LambdaQueryWrapper<UserDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);

        if (userDeviceRepository.selectCount(queryWrapper) == 0) {
            UserDevice userDevice = new UserDevice();
            userDevice.setUserId(userId);
            userDevice.setDeviceId(deviceId);
            userDevice.setRole(USER_DEVICE_ROLE_OWNER);
            userDeviceRepository.insert(userDevice);
            log.info("绑定用户 {} 与设备 {}", userId, deviceId);
        }
    }

    /**
     * 保存设备绑定记录
     */
    private void saveDeviceBinding(Long userId, AddDeviceRequest request) {
        try {
            DeviceBinding binding = new DeviceBinding();
            binding.setDeviceId(request.getDeviceId());
            binding.setDeviceSn(request.getDeviceId());
            binding.setUserId(userId);
            binding.setWifiSsid(request.getWifiSsid());
            binding.setWifiPassword(request.getWifiPassword());
            binding.setStatus(BINDING_STATUS_SUCCESS);
            binding.setProgress(BINDING_PROGRESS_COMPLETE);
            binding.setMessage(BINDING_SUCCESS_MESSAGE);
            deviceBindingRepository.insert(binding);
        } catch (Exception e) {
            // 绑定记录插入失败不影响主流程
            log.warn("保存设备绑定记录失败: {}", e.getMessage());
        }
    }

    /**
     * 查询用户设备的有效云存储订阅
     */
    private CloudSubscription findActiveSubscription(Long userId, String deviceId) {
        LambdaQueryWrapper<CloudSubscription> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CloudSubscription::getUserId, userId)
                .eq(CloudSubscription::getDeviceId, deviceId)
                .orderByDesc(CloudSubscription::getExpireAt)
                .last("LIMIT 1");
        return cloudSubscriptionRepository.selectOne(queryWrapper);
    }

    /**
     * 判断设备是否在线
     */
    private boolean isOnline(Device device) {
        return device.getStatus() != null && device.getStatus() == DEVICE_STATUS_ONLINE;
    }

    /**
     * 格式化时间为ISO8601格式
     */
    private String formatIsoTime(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }
}
