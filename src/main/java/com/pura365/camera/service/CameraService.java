package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.config.StorageConfig;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.CloudVideo;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.DeviceShare;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.model.GetInfoRequest;
import com.pura365.camera.model.GetInfoResponse;
import com.pura365.camera.model.ResetDeviceRequest;
import com.pura365.camera.model.SendMsgRequest;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.CloudVideoRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.DeviceShareRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.util.TimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

@Service
public class CameraService {
    
    private static final Logger log = LoggerFactory.getLogger(CameraService.class);
    
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
    
    /**
     * 获取设备信息
     * 设备调用此接口时，自动新增或更新设备记录
     */
    public GetInfoResponse getDeviceInfo(GetInfoRequest info) {
        log.info("设备请求配置信息 - ID: {}, MAC: {}, Region: {}", info.getId(), info.getMac(), info.getRegion());
        
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
                device.setStatus(1); // 默认离线，等待 MQTT 连接后更新为在线
                device.setEnabled(1);
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
                device.setStatus(1);
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
        
        // 检查云存储订阅状态并配置S3凭证
        if (device != null) {
            configureCloudStorage(device, response);
        } else {
            response.setCloudStorage(0);
        }
        
        // GPT/AI 配置（预留）
        // response.setGPTHostname("ai.pura365.com");
        // response.setGPTKey("gpt-access-key");
        
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
    @Transactional(rollbackFor = Exception.class)
    public int resetDevice(ResetDeviceRequest request) {
        log.info("重置设备 - ID: {}, MAC: {}", request.getId(), request.getMac());
        
        String deviceId = request.getId();
        String mac = request.getMac();
        
        // 1. 验证设备序列号和MAC地址是否匹配
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            log.warn("设备不存在 - ID: {}", deviceId);
            return 1; // 设备不存在
        }
        
        // 验证MAC地址（忽略大小写，移除冒号和横杠）
        String storedMac = normalizeMac(device.getMac());
        String requestMac = normalizeMac(mac);
        if (!storedMac.equals(requestMac)) {
            log.warn("MAC地址不匹配 - ID: {}, 期望: {}, 实际: {}", deviceId, device.getMac(), mac);
            return 2; // MAC地址不匹配
        }
        
        try {
            // 2. 清除设备分享信息（device_share 表）
            int deletedShares = deleteDeviceShares(deviceId);
            log.info("已删除设备分享记录 - ID: {}, 删除数量: {}", deviceId, deletedShares);
            
            // 3. 清除APP连接信息（user_device 表）
            int deletedUserDevices = deleteUserDevices(deviceId);
            log.info("已删除用户设备关联记录 - ID: {}, 删除数量: {}", deviceId, deletedUserDevices);
            
            // 4. 清除云存储历史数据
            // 4.1 删除云视频数据库记录（cloud_video 表）
            int deletedVideos = deleteCloudVideos(deviceId);
            log.info("已删除云视频记录 - ID: {}, 删除数量: {}", deviceId, deletedVideos);
            
            // 4.2 删除云存储中的实际文件
            int deletedFiles = cloudStorageService.deleteAllDeviceVideos(deviceId);
            log.info("已删除云存储文件 - ID: {}, 删除数量: {}", deviceId, deletedFiles);
            
            log.info("设备重置成功 - ID: {}", deviceId);
            return 0; // 成功
            
        } catch (Exception e) {
            log.error("设备重置失败 - ID: {}", deviceId, e);
            throw e; // 抛出异常触发事务回滚
        }
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
     * 用于接收事件信息或AI结果
     * 
     * TODO: 后续需要实现消息推送、存储等逻辑
     */
    public void handleMessage(SendMsgRequest request) {
        log.info("收到摄像头消息 - Topic: {}, Title: {}, Msg: {}", 
                request.getTopic(), request.getTitle(), request.getMsg());
        
        // TODO: 实现以下逻辑
        // 1. 将消息存储到数据库（事件表/告警表）
        // 2. 根据topic判断消息类型并分类处理
        // 3. 如果是告警消息，推送给相关用户（APP推送、短信、邮件等）
        // 4. 如果是AI结果，关联到对应的事件记录
        // 5. 记录消息处理日志
        
        log.info("消息处理完成 - Topic: {}", request.getTopic());
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
     */
    private void configureCloudStorage(Device device, GetInfoResponse response) {
        // 检查是否有有效订阅
        boolean hasSubscription = checkCloudStorageSubscription(device.getId());
        
        if (!hasSubscription) {
            response.setCloudStorage(0); // 未启用
            return;
        }
        
        // 有订阅，设置为连续存储模式
        // TODO: 后续可从 CloudSubscription 表或 设备设置中读取具体模式
        // 1 = 连续存储, 2 = 事件存储
        response.setCloudStorage(1);
        
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

            log.info("配置七牛云S3凭证 - deviceId: {}", device.getId());
        } else {
            // 国外：使用Vultr S3配置
            // Bucket固定为 cloud-storage，摄像头默认使用
            String hostname = vultrConfig.getEndpoint().replace("https://", "").replace("http://", "");
            response.setS3Hostname(hostname);
            response.setS3Region(vultrConfig.getRegion());
            response.setS3AccessKey(vultrConfig.getAccessKey());
            response.setS3SecretKey(vultrConfig.getSecretKey());
            
            log.info("配置Vultr S3凭证 - deviceId: {}", device.getId());
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
