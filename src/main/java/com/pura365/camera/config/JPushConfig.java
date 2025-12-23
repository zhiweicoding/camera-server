package com.pura365.camera.config;

import cn.jpush.api.JPushClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 极光推送配置类
 */
@Configuration
public class JPushConfig {

    @Value("${jpush.app-key}")
    private String appKey;

    @Value("${jpush.master-secret}")
    private String masterSecret;

    @Value("${jpush.apns-production:false}")
    private Boolean apnsProduction;

    /**
     * 创建JPushClient Bean
     */
    @Bean
    public JPushClient jPushClient() {
        return new JPushClient(masterSecret, appKey);
    }

    public String getAppKey() {
        return appKey;
    }

    public String getMasterSecret() {
        return masterSecret;
    }

    public Boolean getApnsProduction() {
        return apnsProduction;
    }
}
