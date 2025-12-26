package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 云存储过期文件定时清理服务
 * 根据设备订阅的套餐循环天数，自动删除过期的云存储文件
 */
@Service
public class CloudVideoCleanupService {

    private static final Logger log = LoggerFactory.getLogger(CloudVideoCleanupService.class);

    @Autowired
    private CloudStorageService cloudStorageService;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    /** 是否启用自动清理 */
    @Value("${cloud.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /**
     * 定时清理云存储过期文件
     * 默认每天凌晨3点执行
     */
    @Scheduled(cron = "${cloud.cleanup.cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            log.info("云存储自动清理已禁用，跳过执行");
            return;
        }

        log.info("开始执行云存储过期文件定时清理任务...");
        
        try {
            int totalDeleted = cleanupAllDevices();
            log.info("云存储过期文件定时清理任务完成，共删除 {} 个文件", totalDeleted);
        } catch (Exception e) {
            log.error("云存储过期文件定时清理任务执行失败", e);
        }
    }

    /**
     * 清理所有设备的过期云存储文件（异步执行）
     * @return 删除的文件总数
     */
    public int cleanupAllDevices() {
        log.info("开始清理所有设备的过期云存储文件...");
        
        // 查询所有有效的云存储订阅
        QueryWrapper<CloudSubscription> wrapper = new QueryWrapper<>();
        wrapper.gt("expire_at", new Date()); // 未过期的订阅
        List<CloudSubscription> subscriptions = cloudSubscriptionRepository.selectList(wrapper);
        
        if (subscriptions.isEmpty()) {
            log.info("没有找到有效的云存储订阅");
            return 0;
        }
        
        // 提取唯一的设备ID
        Set<String> deviceIds = new HashSet<>();
        for (CloudSubscription sub : subscriptions) {
            deviceIds.add(sub.getDeviceId());
        }
        
        log.info("共找到 {} 个设备有云存储订阅，开始异步清理...", deviceIds.size());
        
        // 使用原子计数器统计结果
        AtomicInteger totalDeleted = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // 异步提交每个设备的清理任务
        List<CompletableFuture<Void>> futures = deviceIds.stream()
            .map(deviceId -> cleanupDeviceAsync(deviceId)
                .thenAccept(deleted -> {
                    totalDeleted.addAndGet(deleted);
                    successCount.incrementAndGet();
                })
                .exceptionally(e -> {
                    log.error("异步清理设备 {} 失败", deviceId, e);
                    failCount.incrementAndGet();
                    return null;
                })
            )
            .collect(Collectors.toList());
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("云存储清理完成: 成功 {} 个设备，失败 {} 个设备，共删除 {} 个文件", 
            successCount.get(), failCount.get(), totalDeleted.get());
        
        return totalDeleted.get();
    }
    
    /**
     * 异步清理单个设备的过期云存储文件
     * @param deviceId 设备ID
     * @return CompletableFuture<删除的文件数量>
     */
    @Async("cloudCleanupExecutor")
    public CompletableFuture<Integer> cleanupDeviceAsync(String deviceId) {
        log.info("异步清理设备 {} 的云存储文件", deviceId);
        try {
            int deleted = cloudStorageService.cleanupExpiredVideos(deviceId);
            return CompletableFuture.completedFuture(deleted);
        } catch (Exception e) {
            log.error("异步清理设备 {} 失败", deviceId, e);
            CompletableFuture<Integer> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * 手动触发单个设备的云存储清理
     * @param deviceId 设备ID
     * @return 删除的文件数量
     */
    public int cleanupDevice(String deviceId) {
        log.info("手动触发设备 {} 的云存储清理", deviceId);
        return cloudStorageService.cleanupExpiredVideos(deviceId);
    }
}
