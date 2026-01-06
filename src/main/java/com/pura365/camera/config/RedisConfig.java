package com.pura365.camera.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;

/**
 * Redis 配置类
 * 启动时检查 Redis 连接状态
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void checkRedisConnection() {
        try {
            // 测试 Redis 连接
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equals(pong)) {
                log.info("========================================");
                log.info("Redis 连接成功 - {}:{}", redisHost, redisPort);
                log.info("========================================");
            }
        } catch (Exception e) {
            log.error("========================================");
            log.error("Redis 连接失败 - {}:{}", redisHost, redisPort);
            log.error("错误信息: {}", e.getMessage());
            log.error("========================================");
        }
    }
}
