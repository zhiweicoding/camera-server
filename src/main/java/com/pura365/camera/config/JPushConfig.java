package com.pura365.camera.config;

import cn.jpush.api.JPushClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * JPush configuration.
 */
@Configuration
public class JPushConfig {

    @Value("${jpush.app-key:}")
    private String appKey;

    @Value("${jpush.master-secret:}")
    private String masterSecret;

    @Value("${jpush.apns-production:false}")
    private Boolean apnsProduction;

    @Bean
    @ConditionalOnExpression(
            "'${push.provider:jpush}'.trim().equalsIgnoreCase('jpush') " +
            "|| '${push.provider:jpush}'.trim().equalsIgnoreCase('hybrid') " +
            "|| '${push.provider:jpush}'.trim().equalsIgnoreCase('both') " +
            "|| '${push.provider:jpush}'.trim().equalsIgnoreCase('all')"
    )
    public JPushClient jPushClient() {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(masterSecret)) {
            throw new IllegalStateException("jpush.app-key and jpush.master-secret must be configured when push.provider is jpush/hybrid/both/all");
        }
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
