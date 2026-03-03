package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.config.StorageConfig;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.CloudVideo;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.DeviceShare;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.model.GetInfoRequest;
import com.pura365.camera.model.GetInfoResponse;
import com.pura365.camera.model.ResetDeviceRequest;
import com.pura365.camera.model.SendMsgRequest;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.CloudVideoRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.DeviceShareRepository;
import com.pura365.camera.repository.ManufacturedDeviceRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.util.TimeValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

@Service
public class CameraService {
    
    private static final Logger log = LoggerFactory.getLogger(CameraService.class);
    
    // 自注入，用于调用事务方法（使用@Lazy避免循环依赖）
    @Autowired
    @Lazy
    private CameraService self;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;
    
    @Autowired
    private CloudStorageService cloudStorageService;
    
    @Autowired
    private StorageConfig.QiniuConfig qiniuConfig;
    
    @Autowired
    private StorageConfig.VultrConfig vultrConfig;
    
    @Autowired
    private DeviceShareRepository deviceShareRepository;
    
    @Autowired
    private UserDeviceRepository userDeviceRepository;
    
    @Autowired
    private CloudVideoRepository cloudVideoRepository;
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private ManufacturedDeviceRepository manufacturedDeviceRepository;
    
    /**
     * 获取设备信息
     * 设备调用此接口时，自动新增或更新设备记录
     */
    public GetInfoResponse getDeviceInfo(GetInfoRequest info) {
        log.info("设备请求配置信息 - ID: {}, MAC: {}, Region: {}", info.getId(), info.getMac(), info.getRegion());
        
        // 校验设备ID是否是本系统生产的
        if (!isManufacturedDevice(info.getId())) {
            log.warn("设备ID校验失败，非本系统生产的设备 - ID: {}", info.getId());
            return null;
        }
        
        // 查找或创建设备记录
        Device device = null;
        try {
            device = deviceRepository.selectById(info.getId());
            if (device == null) {
                // 设备不存在，创建新记录
                device = new Device();
                device.setId(info.getId());
                device.setMac(info.getMac() != null ? info.getMac() : "UNKNOWN");
                device.setRegion(info.getRegion());
                device.setStatus(DeviceOnlineStatus.ONLINE);
                device.setEnabled(EnableStatus.ENABLED);
                device.setCreatedAt(LocalDateTime.now());
                device.setUpdatedAt(LocalDateTime.now());
                device.setLastOnlineTime(LocalDateTime.now());
                device.setLastHeartbeatTime(LocalDateTime.now());
                deviceRepository.insert(device);
                log.info("新设备入库成功 - ID: {}, MAC: {}", info.getId(), info.getMac());
            } else {
                // 设备已存在，更新信息
                boolean needUpdate = false;
                if (info.getMac() != null && !info.getMac().isEmpty() && !info.getMac().equals(device.getMac())) {
                    device.setMac(info.getMac());
                    needUpdate = true;
                }
                if (info.getRegion() != null && !info.getRegion().equals(device.getRegion())) {
                    device.setRegion(info.getRegion());
                    needUpdate = true;
                }
                device.setStatus(DeviceOnlineStatus.ONLINE);
                device.setUpdatedAt(LocalDateTime.now());
                device.setLastOnlineTime(LocalDateTime.now());
                device.setLastHeartbeatTime(LocalDateTime.now());
                if (needUpdate) {
                    device.setUpdatedAt(LocalDateTime.now());
                    deviceRepository.updateById(device);
                    log.info("设备信息已更新 - ID: {}, MAC: {}, Region: {}", info.getId(), info.getMac(), info.getRegion());
                }
            }
        } catch (Exception e) {
            log.error("设备入库/更新失败 - ID: {}", info.getId(), e);
        }
        
        // 构建响应
        GetInfoResponse response = new GetInfoResponse();
        response.setDeviceID(info.getId());
        response.setDeviceEnable(true);
        
        // MQTT 配置
        response.setMqttHostname("mqtts://cam.pura365.cn:8883");
        response.setMqttUser("camera_test");
        response.setMqttPass("123456");
        
        // 检查云存储订阅状态并配置S3凭证和AI配置
        if (device != null) {
            configureCloudStorage(device, response);
            configureAI(device, response);
        } else {
            response.setCloudStorage(0);
            response.setNormalAI(false);
        }
        
        return response;
    }

    /**
     * 重置设备
     * 清除该设备的历史数据，包括:
     * 1. APP中该设备的分享信息
     * 2. 之前APP的已连接信息
     * 3. 云存储中的历史数据
     * 
     * @return 0: 成功, 1: 设备不存在, 2: MAC地址不匹配, 3: 清理失败
     */
    public int resetDevice(ResetDeviceRequest request) {
        log.info("resetdevice重置设备 - ID: {}, MAC: {}", request.getId(), request.getMac());
        
        String deviceId = request.getId();
        String mac = request.getMac();
        
        // 1. 验证设备序列号和MAC地址是否匹配
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.error("resetdevice设备不存在 - ID: {}", deviceId);
            return 1; // 设备不存在
        }
        
        // 验证MAC地址（忽略大小写，移除冒号和横杠）
//        String storedMac = normalizeMac(device.getMac());
//        String requestMac = normalizeMac(mac);
//        if (!storedMac.equals(requestMac)) {
//            log.warn("MAC地址不匹配 - ID: {}, 期望: {}, 实际: {}", deviceId, device.getMac(), mac);
//            return 2; // MAC地址不匙配
//        }
        
        try {
            // 2. 先删除云存储文件（耗时操作，但不占用数据库锁）
            // 暂时注释掉，避免删除云录像
            // int deletedFiles = cloudStorageService.deleteAllDeviceVideos(deviceId);
            // log.info("已删除云存储文件 - ID: {}, 删除数量: {}", deviceId, deletedFiles);
            
            // 3. 在短事务中删除数据库记录（通过self代理调用以触发事务）
            self.resetDeviceDatabaseRecords(deviceId);
            
            log.info("设备重置成功 - ID: {}", deviceId);
            return 0; // 成功
            
        } catch (Exception e) {
            log.error("设备重置失败 - ID: {}", deviceId, e);
            return 3; // 清理失败
        }
    }
    
    /**
     * 在事务中删除设备相关的数据库记录（短事务，快速释放锁）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetDeviceDatabaseRecords(String deviceId) {
        // 清除设备分享信息（device_share 表）
        int deletedShares = deleteDeviceShares(deviceId);
        log.info("已删除设备分享记录 - ID: {}, 删除数量: {}", deviceId, deletedShares);
        
        // 清除APP连接信息（user_device 表）
        int deletedUserDevices = deleteUserDevices(deviceId);
        log.info("已删除用户设备关联记录 - ID: {}, 删除数量: {}", deviceId, deletedUserDevices);
        
        // 删除云视频数据库记录（cloud_video 表）
        int deletedVideos = deleteCloudVideos(deviceId);
        log.info("已删除云视频记录 - ID: {}, 删除数量: {}", deviceId, deletedVideos);
    }
    
    /**
     * 标准化MAC地址（转大写，移除分隔符）
     */
    private String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.toUpperCase().replaceAll("[:\\-]", "");
    }
    
    /**
     * 删除设备分享记录
     */
    private int deleteDeviceShares(String deviceId) {
        QueryWrapper<DeviceShare> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        return deviceShareRepository.delete(wrapper);
    }
    
    /**
     * 删除用户设备关联记录
     */
    private int deleteUserDevices(String deviceId) {
        QueryWrapper<UserDevice> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        return userDeviceRepository.delete(wrapper);
    }
    
    /**
     * 删除云视频记录
     */
    private int deleteCloudVideos(String deviceId) {
        QueryWrapper<CloudVideo> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        return cloudVideoRepository.delete(wrapper);
    }
    
    /**
     * 处理摄像头发送的消息通知
     * 用于接收事件信息或AI结果，入库并推送给绑定该设备的所有用户
     */
    public void handleMessage(SendMsgRequest request) {
        String deviceId = request.getTopic();
        String title = request.getTitle();
        String content = request.getMsg();
        String thumbnailUrl = request.getPicurl();
        String videoUrl = request.getVideourl();
        
        // 内容翻译：英文转中文
        content = translateContent(content);
        title = translateContent(title);
        
        // 消息类型
        String messageType = "event";
        
        // 查找该设备绑定的所有用户
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getDeviceId, deviceId);
        List<UserDevice> userDevices = userDeviceRepository.selectList(wrapper);
        
        if (userDevices == null || userDevices.isEmpty()) {
            log.warn("[handleMessage] 设备={} 未绑定用户，跳过推送", deviceId);
            return;
        }
        
        // 为每个用户创建消息并推送
        int successCount = 0;
        for (UserDevice userDevice : userDevices) {
            Long userId = userDevice.getUserId();
            try {
                messageService.createMessageAndPush(userId, deviceId, messageType,
                        title, content, thumbnailUrl, videoUrl, true);
                successCount++;
            } catch (Exception e) {
                log.error("[handleMessage] 推送失败 - 设备={}, 用户={}", deviceId, userId, e);
            }
        }
        
        log.info("[handleMessage] 完成 - 设备={}, 标题={}, 推送成功={}/{}", 
                deviceId, title, successCount, userDevices.size());
    }
    
    /**
     * 翻译消息内容（英文转中文）
     */
    private String translateContent(String content) {
        if (content == null) {
            return null;
        }
        // Motion detected -> 检测到物体移动
        if (content.contains("Motion detected")) {
            content = content.replace("Motion detected", "检测到物体移动");
        }
        return content;
    }
    
    /**
     * 验证时间戳是否有效
     * @deprecated 使用 TimeValidator.isValid() 替代
     */
    @Deprecated
    public boolean validateRequest(Long exp) {
        return TimeValidator.isValid(exp);
    }
    
    /**
     * 检查设备是否有有效的云存储订阅
     */
    private boolean checkCloudStorageSubscription(String deviceId) {
        try {
            QueryWrapper<CloudSubscription> qw = new QueryWrapper<>();
            qw.lambda().eq(CloudSubscription::getDeviceId, deviceId)
                    .orderByDesc(CloudSubscription::getExpireAt)
                    .last("limit 1");
            CloudSubscription subscription = cloudSubscriptionRepository.selectOne(qw);
            
            if (subscription == null) {
                return false;
            }
            
            // 检查是否过期
            Date expireAt = subscription.getExpireAt();
            if (expireAt == null) {
                // 无过期时间，视为永久有效
                return true;
            }
            
            return expireAt.after(new Date());
        } catch (Exception e) {
            log.error("检查云存储订阅状态失败 - deviceId: {}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 配置云存储和S3凭证
     * 根据设备region选择七牛云（国内）或Vultr（国外）
     * 注意：无论是否有订阅，都需要返回S3配置信息
     */
    private void configureCloudStorage(Device device, GetInfoResponse response) {
        // 检查是否有有效订阅
        boolean hasSubscription = checkCloudStorageSubscription(device.getId());
        
        if (hasSubscription) {
            // 有订阅，设置为连续存储模式
            // TODO: 后续可从 CloudSubscription 表或 设备设置中读取具体模式
            // 1 = 连续存储, 2 = 事件存储
            response.setCloudStorage(1);
        } else {
            response.setCloudStorage(0); // 未启用
        }
        
        // 无论是否有订阅，都返回S3配置信息
        // 判断设备区域
        boolean isChina = isChina(device.getRegion());
        
        if (isChina) {
            // 国内：使用七牛云S3兼容配置（当前先写死华南-广东）
            // 七牛云S3兼容域名：https://developer.qiniu.com/kodo/4088/s3-access-domainname
            // Bucket固定为 cloud-storage，摄像头默认使用
            response.setS3Hostname("s3.cn-south-1.qiniucs.com");
            response.setS3Region("cn-south-1");
            response.setS3AccessKey(qiniuConfig.getAccessKey());
            response.setS3SecretKey(qiniuConfig.getSecretKey());

            log.info("配置七牛云S3凭证 - deviceId: {}, hasSubscription: {}", device.getId(), hasSubscription);
        } else {
            // 国外：使用Vultr S3配置
            // Bucket固定为 cloud-storage，摄像头默认使用
            String hostname = vultrConfig.getEndpoint().replace("https://", "").replace("http://", "");
            response.setS3Hostname(hostname);
            response.setS3Region(vultrConfig.getRegion());
            response.setS3AccessKey(vultrConfig.getAccessKey());
            response.setS3SecretKey(vultrConfig.getSecretKey());
            
            log.info("配置Vultr S3凭证 - deviceId: {}, hasSubscription: {}", device.getId(), hasSubscription);
        }
    }
    
    /**
     * 配置AI功能
     * 如果设备有云存储订阅或购买了AI，就启用AI功能
     */
    private void configureAI(Device device, GetInfoResponse response) {
        // 检查是否有有效订阅（当前逻辑：有云存储订阅就启用AI）
        boolean hasSubscription = checkCloudStorageSubscription(device.getId());
        // TODO: 后续如果有独立的AI订阅，也需要检查
        
        if (hasSubscription) {
            response.setNormalAI(true);
            response.setGPTHostname("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
            response.setGPTKey("sk-74ec79b9dc20469fb04e335881e6f731");
            log.info("配置AI功能 - deviceId: {}, 已启用", device.getId());
        } else {
            response.setNormalAI(false);
            log.info("配置AI功能 - deviceId: {}, 未启用", device.getId());
        }
    }
    
    /**
     * 校验设备ID是否是本系统生产的
     * 通过查询 manufactured_device 表确认
     */
    private boolean isManufacturedDevice(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return false;
        }
        try {
            QueryWrapper<com.pura365.camera.domain.ManufacturedDevice> qw = new QueryWrapper<>();
            qw.lambda().eq(com.pura365.camera.domain.ManufacturedDevice::getDeviceId, deviceId);
            return manufacturedDeviceRepository.selectCount(qw) > 0;
        } catch (Exception e) {
            log.error("校验设备ID失败 - deviceId: {}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 判断设备是否在国内
     */
    private boolean isChina(String region) {
        if (region == null || region.isEmpty()) {
            return true; // 默认国内
        }
        String r = region.toLowerCase();
        return r.equals("cn") || r.equals("china") || r.startsWith("cn-");
    }
}
