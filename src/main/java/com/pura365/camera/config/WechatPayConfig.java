package com.pura365.camera.config;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付配置类
 */
@Configuration
public class WechatPayConfig {

    @Value("${wechat.pay.app-id}")
    private String appId;

    @Value("${wechat.pay.mch-id}")
    private String mchId;

    @Value("${wechat.pay.api-key}")
    private String apiKey;

    @Value("${wechat.pay.cert-path:}")
    private String certPath;

    @Value("${wechat.pay.notify-url}")
    private String notifyUrl;

    @Value("${wechat.pay.use-sandbox:true}")
    private Boolean useSandbox;

    /**
     * 创建WxPayService Bean
     */
    @Bean
    public WxPayService wxPayService() {
        WxPayConfig payConfig = new WxPayConfig();
        payConfig.setAppId(appId);
        payConfig.setMchId(mchId);
        payConfig.setMchKey(apiKey);
        payConfig.setNotifyUrl(notifyUrl);
        payConfig.setUseSandboxEnv(useSandbox);
        
        // 如果配置了证书路径,设置证书
        if (certPath != null && !certPath.isEmpty()) {
            payConfig.setKeyPath(certPath);
        }

        WxPayService wxPayService = new WxPayServiceImpl();
        wxPayService.setConfig(payConfig);
        return wxPayService;
    }

    public String getAppId() {
        return appId;
    }

    public String getMchId() {
        return mchId;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }
    
    public Boolean getUseSandbox() {
        return useSandbox;
    }
}
