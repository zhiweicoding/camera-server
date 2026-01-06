package com.pura365.camera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 设备SSID管理服务
 * 用于存储和获取设备的WiFi SSID（用于MQTT消息加解密）
 * 
 * 实现：Redis存储
 */
@Service
public class DeviceSsidService {
    
    private static final Logger log = LoggerFactory.getLogger(DeviceSsidService.class);
    
    /**
     * Redis key前缀
     */
    private static final String KEY_PREFIX = "device:ssid:";
    
    /**
     * SSID过期时间（7天），单位：秒
     */
    private static final long EXPIRE_SECONDS = 7 * 24 * 60 * 60;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 保存设备的SSID
     */
    public void saveSsid(String deviceId, String ssid) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("设备ID为空，无法保存SSID");
            return;
        }
        
        if (ssid == null || ssid.isEmpty()) {
            log.warn("SSID为空，不保存");
            return;
        }
        
        String key = KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, ssid, EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.info("已保存设备 {} 的SSID到Redis（长度: {}）", deviceId, ssid.length());
    }
    
    /**
     * 获取设备的SSID
     * @return SSID，如果不存在返回null
     */
    public String getSsid(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return null;
        }
        
        String key = KEY_PREFIX + deviceId;
        return redisTemplate.opsForValue().get(key);
    }
    
    /**
     * 删除设备的SSID
     */
    public void removeSsid(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }
        
        String key = KEY_PREFIX + deviceId;
        redisTemplate.delete(key);
        log.info("已删除设备 {} 的SSID", deviceId);
    }
    
    /**
     * 获取所有已缓存的设备数量
     */
    public int getCachedDeviceCount() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
    
    /**
     * 清空缓存（仅用于测试）
     */
    public void clearCache() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.warn("已清空所有SSID缓存");
    }
}
