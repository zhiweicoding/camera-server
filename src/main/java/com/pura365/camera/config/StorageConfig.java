package com.pura365.camera.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * 云存储配置类
 * 支持七牛云（国内）和Vultr（国外）双云存储
 */
@Configuration
@Data
public class StorageConfig {

    /**
     * 七牛云配置
     */
    @Configuration
    @ConfigurationProperties(prefix = "storage.qiniu")
    @Data
    public static class QiniuConfig {
        /** 访问密钥 */
        private String accessKey;
        /** 密钥 */
        private String secretKey;
        /** 存储空间名称 */
        private String bucket;
        /** CDN加速域名 */
        private String domain;
        /** 上传域名（可选） */
        private String uploadDomain;
    }

    /**
     * Vultr对象存储配置（S3兼容）
     */
    @Configuration
    @ConfigurationProperties(prefix = "storage.vultr")
    @Data
    public static class VultrConfig {
        /** S3端点 */
        private String endpoint;
        /** 访问密钥 */
        private String accessKey;
        /** 密钥 */
        private String secretKey;
        /** 存储空间名称 */
        private String bucket;
        /** 区域 */
        private String region;
    }
}
