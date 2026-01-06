package com.pura365.camera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 设备配网状态服务
 * 使用Redis存储设备配网状态，用于判断设备是否配网成功
 * 
 * 配网流程：
 * 1. APP提交配网信息 -> 状态设为 PAIRING (配网中)
 * 2. 设备配网成功，发送MQTT CODE 10 status=1 -> 状态设为 SUCCESS (成功)
 * 3. APP轮询设备详情接口，检查配网状态
 *    - 状态为PAIRING：返回null，表示配网中
 *    - 状态为SUCCESS或无状态：返回设备详情
 */
@Service
public class NetworkPairingStatusService {
    
    private static final Logger log = LoggerFactory.getLogger(NetworkPairingStatusService.class);
    
    /**
     * Redis key前缀
     */
    private static final String KEY_PREFIX = "pairing:status:";
    
    /**
     * 配网状态：配网中
     */
    public static final String STATUS_PAIRING = "PAIRING";
    
    /**
     * 配网状态：成功
     */
    public static final String STATUS_SUCCESS = "SUCCESS";
    
    /**
     * 配网状态：失败
     */
    public static final String STATUS_FAILED = "FAILED";
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 配网状态过期时间（秒）
     */
    @Value("${pairing.status.expire-seconds:300}")
    private long expireSeconds;
    
    /**
     * 设置设备配网状态为"配网中"
     * 
     * @param deviceId 设备ID
     */
    public void setPairing(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("设备ID为空，无法设置配网状态");
            return;
        }
        
        String key = KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, STATUS_PAIRING, expireSeconds, TimeUnit.SECONDS);
        log.info("设备 {} 配网状态设置为: {}", deviceId, STATUS_PAIRING);
    }
    
    /**
     * 设置设备配网状态为"成功"
     * 
     * @param deviceId 设备ID
     */
    public void setSuccess(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("设备ID为空，无法更新配网状态");
            return;
        }
        
        String key = KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, STATUS_SUCCESS, expireSeconds, TimeUnit.SECONDS);
        log.info("设备 {} 配网状态更新为: {}", deviceId, STATUS_SUCCESS);
    }
    
    /**
     * 设置设备配网状态为"失败"
     * 
     * @param deviceId 设备ID
     */
    public void setFailed(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("设备ID为空，无法更新配网状态");
            return;
        }
        
        String key = KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, STATUS_FAILED, expireSeconds, TimeUnit.SECONDS);
        log.info("设备 {} 配网状态更新为: {}", deviceId, STATUS_FAILED);
    }
    
    /**
     * 获取设备配网状态
     * 
     * @param deviceId 设备ID
     * @return 配网状态，null表示无状态或已过期
     */
    public String getStatus(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return null;
        }
        
        String key = KEY_PREFIX + deviceId;
        return redisTemplate.opsForValue().get(key);
    }
    
    /**
     * 判断设备是否正在配网中
     * 
     * @param deviceId 设备ID
     * @return true=配网中，false=非配网中（成功、失败或无状态）
     */
    public boolean isPairing(String deviceId) {
        String status = getStatus(deviceId);
        return STATUS_PAIRING.equals(status);
    }
    
    /**
     * 判断设备配网是否成功
     * 
     * @param deviceId 设备ID
     * @return true=成功，false=非成功
     */
    public boolean isSuccess(String deviceId) {
        String status = getStatus(deviceId);
        return STATUS_SUCCESS.equals(status);
    }
    
    /**
     * 清除设备配网状态
     * 
     * @param deviceId 设备ID
     */
    public void clearStatus(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }
        
        String key = KEY_PREFIX + deviceId;
        redisTemplate.delete(key);
        log.info("已清除设备 {} 的配网状态", deviceId);
    }
}
