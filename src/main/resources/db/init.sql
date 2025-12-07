-- 摄像头后端数据库初始化脚本
-- 数据库名称: camera_db

-- 创建数据库
CREATE DATABASE IF NOT EXISTS camera_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE camera_db;

-- ===================================
-- 1. 设备表
-- ===================================
CREATE TABLE IF NOT EXISTS `device` (
    `id` VARCHAR(50) PRIMARY KEY COMMENT '设备序列号',
    `mac` VARCHAR(20) NOT NULL COMMENT 'WiFi MAC地址',
    `ssid` VARCHAR(32) COMMENT 'WiFi SSID（用于MQTT加密）',
    `region` VARCHAR(10) COMMENT '区域（cn/us等）',
    `name` VARCHAR(100) COMMENT '设备名称（用户自定义）',
    `firmware_version` VARCHAR(20) COMMENT '固件版本',
    `status` TINYINT DEFAULT 0 COMMENT '设备状态：0-离线 1-在线',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    
    -- 云存储配置
    `cloud_storage` TINYINT DEFAULT 0 COMMENT '云存储：0-未启用 1-连续存储 2-事件存储',
    `s3_hostname` VARCHAR(255) COMMENT 'S3服务器地址',
    `s3_region` VARCHAR(50) COMMENT 'S3区域',
    `s3_access_key` VARCHAR(100) COMMENT 'S3 Access Key',
    `s3_secret_key` VARCHAR(100) COMMENT 'S3 Secret Key',
    
    -- MQTT配置
    `mqtt_hostname` VARCHAR(255) COMMENT 'MQTT服务器地址',
    `mqtt_username` VARCHAR(50) COMMENT 'MQTT用户名',
    `mqtt_password` VARCHAR(50) COMMENT 'MQTT密码',
    
    -- AI配置
    `ai_enabled` TINYINT DEFAULT 0 COMMENT 'AI功能：0-禁用 1-启用',
    `gpt_hostname` VARCHAR(255) COMMENT 'AI服务器地址',
    `gpt_key` VARCHAR(100) COMMENT 'AI访问Key',
    
    -- 时间戳
    `last_online_time` DATETIME COMMENT '最后在线时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_mac` (`mac`),
    INDEX `idx_status` (`status`),
    INDEX `idx_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- ===================================
-- 2. 配网信息表
-- ===================================
CREATE TABLE IF NOT EXISTS `network_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `ssid` VARCHAR(32) NOT NULL COMMENT 'WiFi SSID',
    `password` VARCHAR(64) COMMENT 'WiFi密码（加密存储）',
    `timezone` VARCHAR(10) COMMENT '时区（如+8）',
    `region` VARCHAR(10) COMMENT '区域',
    `ip_address` VARCHAR(50) COMMENT '设备IP地址',
    `config_status` TINYINT DEFAULT 0 COMMENT '配网状态：0-配网中 1-成功 2-失败',
    `config_method` VARCHAR(20) COMMENT '配网方式：qrcode/ble/audio',
    `config_source` VARCHAR(50) COMMENT '配网来源（APP版本等）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '配网时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_device_id` (`device_id`),
    INDEX `idx_config_status` (`config_status`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_network_config_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配网信息表';

-- ===================================
-- 3. 设备状态历史表
-- ===================================
CREATE TABLE IF NOT EXISTS `device_status_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `status` TINYINT NOT NULL COMMENT '状态：0-离线 1-在线',
    `wifi_ssid` VARCHAR(32) COMMENT 'WiFi SSID',
    `wifi_rssi` INT COMMENT 'WiFi信号强度',
    `firmware_version` VARCHAR(20) COMMENT '固件版本',
    `sd_state` TINYINT COMMENT 'TF卡状态：0-无 1-有',
    `sd_total` BIGINT COMMENT 'TF卡总容量（字节）',
    `sd_free` BIGINT COMMENT 'TF卡剩余容量（字节）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    
    INDEX `idx_device_id` (`device_id`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_device_status_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备状态历史表';

-- ===================================
-- 4. 设备事件/消息表
-- ===================================
CREATE TABLE IF NOT EXISTS `device_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `topic` VARCHAR(100) NOT NULL COMMENT '消息主题',
    `title` VARCHAR(200) COMMENT '消息标题',
    `content` TEXT COMMENT '消息内容',
    `message_type` VARCHAR(20) COMMENT '消息类型：event/alert/ai',
    `severity` TINYINT DEFAULT 0 COMMENT '严重程度：0-普通 1-警告 2-严重',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间',
    
    INDEX `idx_device_id` (`device_id`),
    INDEX `idx_message_type` (`message_type`),
    INDEX `idx_is_read` (`is_read`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_device_message_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备事件/消息表';

-- ===================================
-- 5. 用户表（可选，如果需要用户系统）
-- ===================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（加密）',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `role` TINYINT DEFAULT 1 COMMENT '角色：1-流通用户 2-经销商 3-管理员',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ===================================
-- 6. 用户设备关联表
-- ===================================
CREATE TABLE IF NOT EXISTS `user_device` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `is_owner` TINYINT DEFAULT 0 COMMENT '是否所有者：0-否 1-是',
    `permission` VARCHAR(50) DEFAULT 'VIEW' COMMENT '权限：VIEW/CONTROL/ADMIN',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    
    UNIQUE KEY `uk_user_device` (`user_id`, `device_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_device_id` (`device_id`),
    CONSTRAINT `fk_user_device_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_device_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备关联表';

-- ===================================
-- 7. WebRTC 会话表
-- ===================================
CREATE TABLE IF NOT EXISTS `webrtc_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `session_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '会话ID（sid）',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `user_id` BIGINT COMMENT '用户ID',
    `rtc_server` VARCHAR(255) COMMENT 'WebRTC服务器信息',
    `offer_sdp` TEXT COMMENT 'Offer SDP',
    `answer_sdp` TEXT COMMENT 'Answer SDP',
    `status` VARCHAR(20) DEFAULT 'INIT' COMMENT '状态：INIT/OFFERING/CONNECTED/CLOSED',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_device_id` (`device_id`),
    INDEX `idx_status` (`status`),
    CONSTRAINT `fk_webrtc_session_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebRTC会话表';

-- ===================================
-- 8. APP 消息与版本表
-- ===================================
CREATE TABLE IF NOT EXISTS `app_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT COMMENT '用户ID',
    `device_id` VARCHAR(50) COMMENT '设备序列号',
    `type` VARCHAR(50) COMMENT '消息类型',
    `title` VARCHAR(200) COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `thumbnail_url` VARCHAR(255) COMMENT '缩略图地址',
    `video_url` VARCHAR(255) COMMENT '视频地址',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_app_msg_user` (`user_id`),
    INDEX `idx_app_msg_device` (`device_id`),
    INDEX `idx_app_msg_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP 消息表';

CREATE TABLE IF NOT EXISTS `app_version` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `platform` VARCHAR(20) NOT NULL COMMENT '平台：android/ios',
    `latest_version` VARCHAR(20) NOT NULL COMMENT '最新版本号',
    `min_version` VARCHAR(20) COMMENT '最低支持版本',
    `download_url` VARCHAR(255) COMMENT '下载地址',
    `release_notes` TEXT COMMENT '更新说明',
    `force_update` TINYINT DEFAULT 0 COMMENT '是否强制更新：0-否 1-是',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_app_ver_platform` (`platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP 版本信息表';

-- ===================================
-- 9. 生产与销售相关表（装机商 / 销售商 / 批次 / 设备）
-- ===================================
CREATE TABLE IF NOT EXISTS `assembler` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `assembler_code` VARCHAR(8) NOT NULL COMMENT '装机商代码',
    `assembler_name` VARCHAR(100) NOT NULL COMMENT '装机商名称',
    `contact_person` VARCHAR(50) COMMENT '联系人',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_assembler_code` (`assembler_code`),
    INDEX `idx_assembler_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装机商表';

CREATE TABLE IF NOT EXISTS `vendor` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `vendor_code` VARCHAR(8) NOT NULL COMMENT '销售商代码',
    `vendor_name` VARCHAR(100) NOT NULL COMMENT '销售商名称',
    `contact_person` VARCHAR(50) COMMENT '联系人',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `address` VARCHAR(255) COMMENT '地址',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_vendor_code` (`vendor_code`),
    INDEX `idx_vendor_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售商表';

CREATE TABLE IF NOT EXISTS `device_production_batch` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号',
    `network_lens` VARCHAR(4) COMMENT '网络+镜头(第1-2位)',
    `device_form` VARCHAR(4) COMMENT '设备形态(第3位)',
    `special_req` VARCHAR(4) COMMENT '特殊要求(第4位)',
    `assembler_code` VARCHAR(8) COMMENT '装机商代码(第5位)',
    `vendor_code` VARCHAR(8) COMMENT '销售商代码(第6-7位)',
    `reserved` VARCHAR(8) COMMENT '预留位(第8位)',
    `quantity` INT COMMENT '生产数量',
    `start_serial` INT COMMENT '起始序列号',
    `end_serial` INT COMMENT '结束序列号',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/producing/completed',
    `remark` VARCHAR(255) COMMENT '备注',
    `created_by` VARCHAR(50) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    INDEX `idx_batch_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备生产批次表';

CREATE TABLE IF NOT EXISTS `manufactured_device` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) NOT NULL COMMENT '完整设备ID(16位)',
    `batch_id` BIGINT COMMENT '关联批次ID',
    `network_lens` VARCHAR(4) COMMENT '网络+镜头',
    `device_form` VARCHAR(4) COMMENT '设备形态',
    `special_req` VARCHAR(4) COMMENT '特殊要求',
    `assembler_code` VARCHAR(8) COMMENT '装机商代码',
    `vendor_code` VARCHAR(8) COMMENT '销售商代码',
    `serial_no` VARCHAR(16) COMMENT '序列号(第9-16位)',
    `mac_address` VARCHAR(32) COMMENT 'MAC地址',
    `status` VARCHAR(20) DEFAULT 'manufactured' COMMENT '状态: manufactured/activated/bound',
    `manufactured_at` DATETIME COMMENT '生产时间',
    `activated_at` DATETIME COMMENT '激活时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_manufactured_device_id` (`device_id`),
    INDEX `idx_manufactured_batch` (`batch_id`),
    INDEX `idx_manufactured_mac` (`mac_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产设备表';

-- ===================================
-- 10. 云存储与录像相关表
-- ===================================
CREATE TABLE IF NOT EXISTS `cloud_plan` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `plan_id` VARCHAR(50) NOT NULL COMMENT '套餐ID',
    `name` VARCHAR(100) NOT NULL COMMENT '套餐名称',
    `description` TEXT COMMENT '描述',
    `storage_days` INT COMMENT '存储天数',
    `price` DECIMAL(10,2) COMMENT '现价',
    `original_price` DECIMAL(10,2) COMMENT '原价',
    `period` VARCHAR(20) COMMENT '计费周期',
    `features` TEXT COMMENT '特性',
    
    UNIQUE KEY `uk_cloud_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云存储套餐表';

CREATE TABLE IF NOT EXISTS `cloud_subscription` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `plan_id` VARCHAR(50) NOT NULL COMMENT '套餐ID',
    `plan_name` VARCHAR(100) COMMENT '套餐名称快照',
    `expire_at` DATETIME COMMENT '到期时间',
    `auto_renew` TINYINT DEFAULT 0 COMMENT '是否自动续费',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_cloud_sub_user` (`user_id`),
    INDEX `idx_cloud_sub_device` (`device_id`),
    INDEX `idx_cloud_sub_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云存储订阅表';

CREATE TABLE IF NOT EXISTS `cloud_video` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `video_id` VARCHAR(50) NOT NULL COMMENT '云录像ID',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `type` VARCHAR(20) COMMENT '类型',
    `title` VARCHAR(200) COMMENT '标题',
    `thumbnail` VARCHAR(255) COMMENT '缩略图',
    `video_url` VARCHAR(255) COMMENT '视频地址',
    `duration` INT COMMENT '时长(秒)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_cloud_video_device` (`device_id`),
    INDEX `idx_cloud_video_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云录像表';

CREATE TABLE IF NOT EXISTS `local_video` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `video_id` VARCHAR(50) NOT NULL COMMENT '本地录像ID',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `type` VARCHAR(20) COMMENT '类型',
    `title` VARCHAR(200) COMMENT '标题',
    `thumbnail` VARCHAR(255) COMMENT '缩略图',
    `video_url` VARCHAR(255) COMMENT '视频地址',
    `duration` INT COMMENT '时长(秒)',
    `size` BIGINT COMMENT '大小(字节)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_local_video_device` (`device_id`),
    INDEX `idx_local_video_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地录像表';

CREATE TABLE IF NOT EXISTS `device_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `record_id` VARCHAR(50) NOT NULL COMMENT '录制ID',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `status` VARCHAR(20) COMMENT '状态',
    `video_url` VARCHAR(255) COMMENT '视频地址',
    `duration` INT COMMENT '时长(秒)',
    `size` BIGINT COMMENT '大小(字节)',
    `started_at` DATETIME COMMENT '开始时间',
    `ended_at` DATETIME COMMENT '结束时间',
    
    UNIQUE KEY `uk_device_record_id` (`record_id`),
    INDEX `idx_device_record_device` (`device_id`),
    INDEX `idx_device_record_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备录像记录表';

CREATE TABLE IF NOT EXISTS `live_stream` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `stream_id` VARCHAR(50) NOT NULL COMMENT '流ID',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `user_id` BIGINT COMMENT '用户ID',
    `protocol` VARCHAR(20) COMMENT '协议(webRTC/RTMP等)',
    `quality` VARCHAR(20) COMMENT '清晰度',
    `signaling_url` VARCHAR(255) COMMENT '信令地址',
    `ice_servers` TEXT COMMENT 'ICE服务器配置(JSON)',
    `expires_at` DATETIME COMMENT '过期时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    UNIQUE KEY `uk_live_stream_id` (`stream_id`),
    INDEX `idx_live_stream_device` (`device_id`),
    INDEX `idx_live_stream_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播流表';

-- ===================================
-- 11. 设备绑定 / 设置 / 反馈 / WiFi 历史
-- ===================================
CREATE TABLE IF NOT EXISTS `device_binding` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) COMMENT '设备ID',
    `device_sn` VARCHAR(50) COMMENT '设备序列号',
    `user_id` BIGINT COMMENT '用户ID',
    `wifi_ssid` VARCHAR(64) COMMENT 'WiFi SSID',
    `wifi_password` VARCHAR(128) COMMENT 'WiFi 密码(加密)',
    `status` VARCHAR(20) COMMENT '状态',
    `progress` INT COMMENT '进度(百分比)',
    `message` VARCHAR(255) COMMENT '状态信息',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_binding_user` (`user_id`),
    INDEX `idx_binding_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备绑定表';

CREATE TABLE IF NOT EXISTS `device_settings` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备序列号',
    `motion_detection` TINYINT COMMENT '移动侦测开关',
    `night_vision` TINYINT COMMENT '夜视开关',
    `audio_enabled` TINYINT COMMENT '声音开关',
    `flip_image` TINYINT COMMENT '画面翻转',
    `sensitivity` VARCHAR(20) COMMENT '灵敏度',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_device_settings_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备设置表';

CREATE TABLE IF NOT EXISTS `feedback` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `feedback_id` VARCHAR(50) NOT NULL COMMENT '反馈ID',
    `user_id` BIGINT COMMENT '用户ID',
    `content` TEXT COMMENT '反馈内容',
    `contact` VARCHAR(100) COMMENT '联系方式',
    `images` TEXT COMMENT '图片列表(JSON)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_feedback_user` (`user_id`),
    INDEX `idx_feedback_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';

CREATE TABLE IF NOT EXISTS `wifi_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT COMMENT '用户ID',
    `ssid` VARCHAR(64) NOT NULL COMMENT 'WiFi SSID',
    `signal` INT COMMENT '信号强度',
    `security` VARCHAR(32) COMMENT '加密方式',
    `is_connected` TINYINT DEFAULT 0 COMMENT '是否已连接过',
    `last_used_at` DATETIME COMMENT '最后使用时间',
    
    INDEX `idx_wifi_user` (`user_id`),
    INDEX `idx_wifi_ssid` (`ssid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WiFi 历史记录表';

-- ===================================
-- 12. 支付与用户认证相关表
-- ===================================
CREATE TABLE IF NOT EXISTS `payment_order` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `order_id` VARCHAR(64) NOT NULL COMMENT '业务订单号',
    `user_id` BIGINT COMMENT '用户ID',
    `device_id` VARCHAR(50) COMMENT '设备序列号',
    `product_type` VARCHAR(50) COMMENT '产品类型',
    `product_id` VARCHAR(64) COMMENT '产品ID',
    `amount` DECIMAL(10,2) COMMENT '金额',
    `currency` VARCHAR(10) COMMENT '币种',
    `status` VARCHAR(20) COMMENT '状态',
    `payment_method` VARCHAR(20) COMMENT '支付方式',
    `third_order_id` VARCHAR(64) COMMENT '第三方订单号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `paid_at` DATETIME COMMENT '支付时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_payment_order_id` (`order_id`),
    INDEX `idx_payment_user` (`user_id`),
    INDEX `idx_payment_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

CREATE TABLE IF NOT EXISTS `payment_wechat` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `order_id` VARCHAR(64) NOT NULL COMMENT '业务订单号',
    `prepay_id` VARCHAR(128) COMMENT '微信预支付ID',
    `raw_response` TEXT COMMENT '微信原始响应JSON',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_pay_wechat_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信支付扩展表';

CREATE TABLE IF NOT EXISTS `user_auth` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `auth_type` VARCHAR(20) NOT NULL COMMENT '登录类型：wechat/apple/google/sms',
    `open_id` VARCHAR(128) NOT NULL COMMENT '第三方唯一标识',
    `union_id` VARCHAR(128) COMMENT 'unionId',
    `extra_info` TEXT COMMENT '原始JSON',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_user_auth_user` (`user_id`),
    INDEX `idx_user_auth_type` (`auth_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户第三方登录表';

CREATE TABLE IF NOT EXISTS `user_token` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `access_token` VARCHAR(255) NOT NULL COMMENT '访问Token',
    `refresh_token` VARCHAR(255) COMMENT '刷新Token',
    `expires_at` DATETIME COMMENT '过期时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_user_token_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Token表';

ALTER TABLE `user`
    ADD COLUMN `uid` VARCHAR(50) COMMENT '业务用户ID' AFTER `id`,
    ADD COLUMN `nickname` VARCHAR(100) COMMENT '昵称' AFTER `role`,
    ADD COLUMN `avatar` VARCHAR(255) COMMENT '头像URL' AFTER `nickname`,
    CHANGE COLUMN `password` `password_hash` VARCHAR(100) COMMENT '密码（BCrypt加密）',
    MODIFY COLUMN `username` VARCHAR(50) NULL COMMENT '用户名';

-- ===================================
-- 插入测试数据（可选）
-- ===================================

-- 测试设备
INSERT INTO `device` (`id`, `mac`, `ssid`, `region`, `name`, `enabled`) VALUES
('test001', '00:11:22:33:44:55', 'AOCCX', 'cn', '测试摄像头1', 1),
('test002', 'AA:BB:CC:DD:EE:FF', 'TestWiFi', 'cn', '测试摄像头2', 1)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;

-- 测试用户
INSERT INTO `user` (`username`, `password`, `email`, `role`) VALUES
('admin', '$2a$10$example_hashed_password', 'admin@example.com', 3),
('testuser', '$2a$10$example_hashed_password', 'test@example.com', 1)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;
