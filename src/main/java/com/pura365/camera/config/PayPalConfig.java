package com.pura365.camera.config;

import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.OAuthTokenCredential;
import com.paypal.base.rest.PayPalRESTException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * PayPal支付配置类
 */
@Configuration
public class PayPalConfig {

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.mode}")
    private String mode;

    @Value("${paypal.return-url}")
    private String returnUrl;

    @Value("${paypal.cancel-url}")
    private String cancelUrl;

    /**
     * 创建PayPal API上下文
     */
    @Bean
    public APIContext apiContext() throws PayPalRESTException {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("mode", mode);
        
        APIContext context = new APIContext(clientId, clientSecret, mode);
        context.setConfigurationMap(configMap);
        return context;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public String getMode() {
        return mode;
    }
}
