-- 用户推送Token表
-- 用于存储用户设备的极光推送Registration ID
-- 支持一个用户多个设备的推送管理

CREATE TABLE IF NOT EXISTS `user_push_token` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `device_type` VARCHAR(20) NOT NULL COMMENT '设备类型: iOS/Android',
    `registration_id` VARCHAR(255) NOT NULL COMMENT '极光推送Registration ID',
    `app_version` VARCHAR(20) COMMENT 'APP版本号',
    `device_model` VARCHAR(100) COMMENT '设备型号',
    `os_version` VARCHAR(50) COMMENT '系统版本',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_user_registration` (`user_id`, `registration_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_registration_id` (`registration_id`),
    INDEX `idx_enabled` (`enabled`),
    CONSTRAINT `fk_push_token_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推送Token表';
