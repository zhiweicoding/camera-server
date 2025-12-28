package com.pura365.camera.config;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PayPal 支付配置
 */
@Configuration
public class PaypalConfig {

    @Value("${paypal.mode:sandbox}")
    private String mode;

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.return-url}")
    private String returnUrl;

    @Value("${paypal.cancel-url}")
    private String cancelUrl;

    @Value("${paypal.webhook-id:}")
    private String webhookId;

    @Bean
    public PayPalHttpClient payPalHttpClient() {
        PayPalEnvironment environment;
        if ("live".equalsIgnoreCase(mode)) {
            environment = new PayPalEnvironment.Live(clientId, clientSecret);
        } else {
            environment = new PayPalEnvironment.Sandbox(clientId, clientSecret);
        }
        return new PayPalHttpClient(environment);
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public String getWebhookId() {
        return webhookId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public boolean isSandbox() {
        return !"live".equalsIgnoreCase(mode);
    }
}
