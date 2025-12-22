package com.pura365.camera.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 第三方登录配置（微信/Apple/Google）
 */
@Configuration
@ConfigurationProperties(prefix = "oauth")
public class OAuthConfig {

    private WeChatConfig wechat = new WeChatConfig();
    private AppleConfig apple = new AppleConfig();
    private GoogleConfig google = new GoogleConfig();

    public WeChatConfig getWechat() {
        return wechat;
    }

    public void setWechat(WeChatConfig wechat) {
        this.wechat = wechat;
    }

    public AppleConfig getApple() {
        return apple;
    }

    public void setApple(AppleConfig apple) {
        this.apple = apple;
    }

    public GoogleConfig getGoogle() {
        return google;
    }

    public void setGoogle(GoogleConfig google) {
        this.google = google;
    }

    /**
     * 微信登录配置
     */
    public static class WeChatConfig {
        /** 微信开放平台 App ID */
        private String appId;
        /** 微信开放平台 App Secret */
        private String appSecret;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }
    }

    /**
     * Apple 登录配置
     */
    public static class AppleConfig {
        /** Apple Developer Team ID */
        private String teamId;
        /** Services ID (client_id) */
        private String clientId;
        /** Key ID */
        private String keyId;
        /** Private Key 文件路径或内容 */
        private String privateKey;

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }
    }

    /**
     * Google 登录配置
     */
    public static class GoogleConfig {
        /** Google OAuth Client ID (iOS) */
        private String clientIdIos;
        /** Google OAuth Client ID (Android) */
        private String clientIdAndroid;
        /** Google OAuth Client ID (Web) */
        private String clientIdWeb;
        /** Google OAuth Client Secret (Web) */
        private String clientSecretWeb;
        /** Google OAuth 回调地址 */
        private String redirectUri;

        public String getClientIdIos() {
            return clientIdIos;
        }

        public void setClientIdIos(String clientIdIos) {
            this.clientIdIos = clientIdIos;
        }

        public String getClientIdAndroid() {
            return clientIdAndroid;
        }

        public void setClientIdAndroid(String clientIdAndroid) {
            this.clientIdAndroid = clientIdAndroid;
        }

        public String getClientIdWeb() {
            return clientIdWeb;
        }

        public void setClientIdWeb(String clientIdWeb) {
            this.clientIdWeb = clientIdWeb;
        }

        public String getClientSecretWeb() {
            return clientSecretWeb;
        }

        public void setClientSecretWeb(String clientSecretWeb) {
            this.clientSecretWeb = clientSecretWeb;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }
}
