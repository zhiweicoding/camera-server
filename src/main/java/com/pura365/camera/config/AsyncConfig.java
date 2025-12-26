package com.pura365.camera.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 */
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 云存储清理任务专用线程池
     * 特点：
     * - 核心线程数较小（2），避免占用过多资源
     * - 最大线程数适中（5），限制并发清理数量
     * - 队列容量较大（100），支持批量任务排队
     * - 拒绝策略：调用者运行，防止任务丢失
     */
    @Bean("cloudCleanupExecutor")
    public Executor cloudCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("cloud-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("云存储清理线程池已初始化: coreSize={}, maxSize={}, queueCapacity={}", 
            executor.getCorePoolSize(), executor.getMaxPoolSize(), 100);
        
        return executor;
    }
}
