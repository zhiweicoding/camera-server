-- camera_app 完整数据库建表脚本（基于实体类生成）
-- 生成时间: 2026-02-04
-- 使用方式：
--   1. CREATE DATABASE IF NOT EXISTS camera_app DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
--   2. USE camera_app;
--   3. 执行本脚本。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 设备表：device（新增摄像头状态、灯泡配置等字段）
-- =====================================================
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device` (
  `id` varchar(50) NOT NULL COMMENT '设备序列号，主键',
  `mac` varchar(20) DEFAULT NULL COMMENT '设备 MAC 地址',
  `ssid` varchar(32) DEFAULT NULL COMMENT 'WiFi SSID',
  `region` varchar(10) DEFAULT NULL COMMENT '区域，如 cn/us',
  `name` varchar(100) DEFAULT NULL COMMENT '设备名称',
  `firmware_version` varchar(20) DEFAULT NULL COMMENT '固件版本',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '设备状态 0-离线 1-在线',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
  `cloud_storage` tinyint(1) NOT NULL DEFAULT 0 COMMENT '云存储开关 0-关 1-开',
  `s3_hostname` varchar(255) DEFAULT NULL COMMENT '对象存储地址',
  `s3_region` varchar(50) DEFAULT NULL COMMENT '对象存储区域',
  `s3_access_key` varchar(100) DEFAULT NULL COMMENT '对象存储AK',
  `s3_secret_key` varchar(100) DEFAULT NULL COMMENT '对象存储SK',
  `mqtt_hostname` varchar(255) DEFAULT NULL COMMENT 'MQTT 服务器地址',
  `mqtt_username` varchar(50) DEFAULT NULL COMMENT 'MQTT 用户名',
  `mqtt_password` varchar(50) DEFAULT NULL COMMENT 'MQTT 密码',
  `ai_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'AI 功能开关 0-关 1-开',
  `gpt_hostname` varchar(255) DEFAULT NULL COMMENT 'AI 服务地址',
  `gpt_key` varchar(100) DEFAULT NULL COMMENT 'AI 服务 Key',
  `last_online_time` datetime DEFAULT NULL COMMENT '最近在线时间',
  -- 摄像头实时状态字段
  `network_type` varchar(10) DEFAULT NULL COMMENT '网络类型: 4G/wifi',
  `wifi_rssi` int DEFAULT NULL COMMENT 'WiFi信号强度(RSSI)',
  `sd_state` tinyint(1) DEFAULT NULL COMMENT 'TF卡状态: 0-无 1-有',
  `sd_capacity` bigint DEFAULT NULL COMMENT 'TF卡总块数',
  `sd_block_size` bigint DEFAULT NULL COMMENT 'TF卡块大小(字节)',
  `sd_free` bigint DEFAULT NULL COMMENT 'TF卡空闲块数',
  `rotate` tinyint(1) DEFAULT 0 COMMENT '画面旋转: 0-正常 1-旋转180度',
  `light_led` tinyint(1) DEFAULT 0 COMMENT '照明灯状态: 0-关闭 1-开启',
  `white_led` tinyint(1) DEFAULT 0 COMMENT '白光灯状态: 0-禁用 1-启用',
  `bulbs_en` tinyint(1) DEFAULT 0 COMMENT '灯泡功能状态: 0-不支持 1-支持',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `free_cloud_claimed` tinyint(1) DEFAULT 0 COMMENT '是否已领取7天免费云录像: 0-未领取 1-已领取',
  `last_preview_url` varchar(500) DEFAULT NULL COMMENT '上一次预览画面URL',
  -- 灯泡配置字段
  `bulb_detect` tinyint(1) DEFAULT 0 COMMENT '灯泡工作模式: 0-手动 1-环境光自动 2-定时',
  `bulb_brightness` int DEFAULT 50 COMMENT '灯泡亮度: 0-100',
  `bulb_enable` tinyint(1) DEFAULT 0 COMMENT '灯泡开关状态: 0-关闭 1-开启',
  `bulb_time_on1` varchar(10) DEFAULT NULL COMMENT '定时一开启时间 hh:mm',
  `bulb_time_off1` varchar(10) DEFAULT NULL COMMENT '定时一关闭时间 hh:mm',
  `bulb_time_on2` varchar(10) DEFAULT NULL COMMENT '定时二开启时间 hh:mm',
  `bulb_time_off2` varchar(10) DEFAULT NULL COMMENT '定时二关闭时间 hh:mm',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_mac` (`mac`),
  KEY `idx_device_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- =====================================================
-- 2. 配网记录表：network_config
-- =====================================================
DROP TABLE IF EXISTS `network_config`;
CREATE TABLE `network_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `ssid` varchar(32) NOT NULL COMMENT 'WiFi SSID',
  `password` varchar(64) DEFAULT NULL COMMENT 'WiFi 密码',
  `timezone` varchar(10) DEFAULT NULL COMMENT '时区',
  `region` varchar(10) DEFAULT NULL COMMENT '区域',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP 地址',
  `config_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '配网状态 0-配网中 1-成功 2-失败',
  `config_method` varchar(20) DEFAULT NULL COMMENT '配网方式 qrcode/ble/audio',
  `config_source` varchar(50) DEFAULT NULL COMMENT '配网来源',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_network_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备配网记录表';

-- =====================================================
-- 3. 设备消息表：device_message
-- =====================================================
DROP TABLE IF EXISTS `device_message`;
CREATE TABLE `device_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `topic` varchar(100) NOT NULL COMMENT '来源主题',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `message_type` varchar(20) DEFAULT NULL COMMENT '消息类型 event/alert/ai',
  `severity` tinyint(1) NOT NULL DEFAULT 0 COMMENT '严重级别 0-普通 1-警告 2-严重',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_msg_device` (`device_id`),
  KEY `idx_msg_type` (`message_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备消息表';

-- =====================================================
-- 4. 用户表：user（新增角色、装机商/经销商关联等字段）
-- =====================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `uid` varchar(64) NOT NULL COMMENT '业务用户ID',
  `username` varchar(64) DEFAULT NULL COMMENT '登录账号',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `password_hash` varchar(255) DEFAULT NULL COMMENT '密码哈希',
  `role` tinyint(1) DEFAULT 1 COMMENT '角色: 1-流通用户 2-经销商 3-管理员 4-装机商',
  `installer_id` bigint DEFAULT NULL COMMENT '关联装机商ID',
  `dealer_id` bigint DEFAULT NULL COMMENT '关联经销商ID',
  `is_installer` tinyint(1) DEFAULT 0 COMMENT '是否为装机商身份: 0-否 1-是',
  `is_dealer` tinyint(1) DEFAULT 0 COMMENT '是否为经销商身份: 0-否 1-是',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_uid` (`uid`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =====================================================
-- 5. 用户设备关联表：user_device（新增permission字段）
-- =====================================================
DROP TABLE IF EXISTS `user_device`;
CREATE TABLE `user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT 'user.id',
  `device_id` varchar(50) NOT NULL COMMENT 'device.id',
  `role` varchar(20) DEFAULT 'OWNER' COMMENT '角色: OWNER-所有者 VIEWER-查看者',
  `permission` varchar(20) DEFAULT 'FULL_CONTROL' COMMENT '权限: VIEW_ONLY-仅查看 FULL_CONTROL-完全控制',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  KEY `idx_ud_user` (`user_id`),
  KEY `idx_ud_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-设备关联表';

-- =====================================================
-- 6. 用户第三方登录表：user_auth
-- =====================================================
DROP TABLE IF EXISTS `user_auth`;
CREATE TABLE `user_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT 'user.id',
  `auth_type` varchar(20) NOT NULL COMMENT '登录类型 wechat/apple/google/sms',
  `open_id` varchar(128) NOT NULL COMMENT '第三方唯一用户标识',
  `union_id` varchar(128) DEFAULT NULL COMMENT '微信 unionid 等',
  `extra_info` text COMMENT '第三方返回的原始信息 JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ua_user` (`user_id`),
  UNIQUE KEY `uk_ua_type_open` (`auth_type`,`open_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户第三方登录表';

-- =====================================================
-- 7. 用户 Token 表：user_token
-- =====================================================
DROP TABLE IF EXISTS `user_token`;
CREATE TABLE `user_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT 'user.id',
  `access_token` varchar(255) NOT NULL COMMENT '访问 token',
  `refresh_token` varchar(255) NOT NULL COMMENT '刷新 token',
  `expires_at` datetime NOT NULL COMMENT 'access_token 过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ut_user` (`user_id`),
  UNIQUE KEY `uk_ut_refresh` (`refresh_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 Token 表';

-- =====================================================
-- 8. 用户推送Token表：user_push_token（新表）
-- =====================================================
DROP TABLE IF EXISTS `user_push_token`;
CREATE TABLE `user_push_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `device_type` varchar(20) DEFAULT NULL COMMENT '设备类型: iOS/Android',
  `registration_id` varchar(255) NOT NULL COMMENT '极光推送Registration ID',
  `app_version` varchar(50) DEFAULT NULL COMMENT 'APP版本号',
  `device_model` varchar(100) DEFAULT NULL COMMENT '设备型号',
  `os_version` varchar(100) DEFAULT NULL COMMENT '系统版本',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_upt_user` (`user_id`),
  KEY `idx_upt_reg` (`registration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推送Token表';

-- =====================================================
-- 9. 设备设置表：device_settings
-- =====================================================
DROP TABLE IF EXISTS `device_settings`;
CREATE TABLE `device_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `motion_detection` tinyint(1) NOT NULL DEFAULT 0 COMMENT '移动侦测开关',
  `night_vision` tinyint(1) NOT NULL DEFAULT 0 COMMENT '夜视开关',
  `audio_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '拾音开关',
  `flip_image` tinyint(1) NOT NULL DEFAULT 0 COMMENT '画面翻转',
  `sensitivity` varchar(16) DEFAULT 'medium' COMMENT '灵敏度 low/medium/high',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ds_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备设置表';

-- =====================================================
-- 10. 本地录像表：local_video
-- =====================================================
DROP TABLE IF EXISTS `local_video`;
CREATE TABLE `local_video` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `video_id` varchar(64) NOT NULL COMMENT '业务录像ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `type` varchar(20) NOT NULL COMMENT 'alarm/manual/scheduled/continuous',
  `title` varchar(128) DEFAULT NULL COMMENT '标题',
  `thumbnail` varchar(255) DEFAULT NULL COMMENT '缩略图URL',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频URL',
  `duration` int DEFAULT NULL COMMENT '时长（秒）',
  `size` bigint DEFAULT NULL COMMENT '大小（字节）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lv_video` (`video_id`),
  KEY `idx_lv_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地录像表';

-- =====================================================
-- 11. 设备录制会话表：device_record
-- =====================================================
DROP TABLE IF EXISTS `device_record`;
CREATE TABLE `device_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `record_id` varchar(64) NOT NULL COMMENT '业务录制ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `status` varchar(20) NOT NULL DEFAULT 'recording' COMMENT 'recording/stopped',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频URL',
  `duration` int DEFAULT NULL COMMENT '时长（秒）',
  `size` bigint DEFAULT NULL COMMENT '大小（字节）',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_record` (`record_id`),
  KEY `idx_dr_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备录制会话表';

-- =====================================================
-- 12. App 消息表：app_message
-- =====================================================
DROP TABLE IF EXISTS `app_message`;
CREATE TABLE `app_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `device_id` varchar(50) DEFAULT NULL COMMENT '设备ID',
  `type` varchar(20) DEFAULT NULL COMMENT 'alarm/system/promotion',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `thumbnail_url` varchar(255) DEFAULT NULL COMMENT '缩略图URL',
  `video_url` varchar(255) DEFAULT NULL COMMENT '关联视频URL',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_am_user` (`user_id`),
  KEY `idx_am_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 消息表';

-- =====================================================
-- 13. 云存储套餐表：cloud_plan（新增type/sort_order/status/plan_cost字段）
-- =====================================================
DROP TABLE IF EXISTS `cloud_plan`;
CREATE TABLE `cloud_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` varchar(64) NOT NULL COMMENT '业务套餐ID',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `storage_days` int DEFAULT NULL COMMENT '存储天数',
  `price` decimal(10,2) DEFAULT NULL COMMENT '当前价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `plan_cost` decimal(10,2) DEFAULT NULL COMMENT '套餐成本',
  `period` varchar(20) DEFAULT NULL COMMENT '周期 month/year',
  `features` text COMMENT '特性JSON',
  `type` varchar(20) DEFAULT 'motion' COMMENT '套餐类型: motion-动态录像 fulltime-全天录像 traffic-4G流量',
  `sort_order` int DEFAULT 0 COMMENT '排序序号',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cp_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云存储套餐表';

-- =====================================================
-- 14. 云存储订阅表：cloud_subscription
-- =====================================================
DROP TABLE IF EXISTS `cloud_subscription`;
CREATE TABLE `cloud_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `plan_id` varchar(64) NOT NULL COMMENT '套餐ID',
  `plan_name` varchar(64) DEFAULT NULL COMMENT '套餐名称',
  `expire_at` datetime DEFAULT NULL COMMENT '到期时间',
  `auto_renew` tinyint(1) NOT NULL DEFAULT 0 COMMENT '自动续费',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_cs_user` (`user_id`),
  KEY `idx_cs_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云存储订阅表';

-- =====================================================
-- 15. 云录像表：cloud_video
-- =====================================================
DROP TABLE IF EXISTS `cloud_video`;
CREATE TABLE `cloud_video` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `video_id` varchar(64) NOT NULL COMMENT '业务视频ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `type` varchar(20) DEFAULT NULL COMMENT 'alarm/manual/scheduled',
  `title` varchar(128) DEFAULT NULL COMMENT '标题',
  `thumbnail` varchar(255) DEFAULT NULL COMMENT '缩略图URL',
  `video_url` varchar(255) DEFAULT NULL COMMENT '视频URL',
  `duration` int DEFAULT NULL COMMENT '时长（秒）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cv_video` (`video_id`),
  KEY `idx_cv_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云录像表';

-- =====================================================
-- 16. 支付订单表：payment_order（新增大量佣金相关字段）
-- =====================================================
DROP TABLE IF EXISTS `payment_order`;
CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) NOT NULL COMMENT '业务订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `device_id` varchar(50) DEFAULT NULL COMMENT '设备ID',
  `commission_rate` decimal(5,2) DEFAULT NULL COMMENT '佣金比例(快照)',
  `installer_id` bigint DEFAULT NULL COMMENT '装机商ID',
  `installer_code` varchar(20) DEFAULT NULL COMMENT '装机商代码',
  `installer_rate` decimal(5,2) DEFAULT NULL COMMENT '装机商分润比例',
  `installer_amount` decimal(10,2) DEFAULT NULL COMMENT '装机商分润金额',
  `dealer_id` bigint DEFAULT NULL COMMENT '经销商ID',
  `dealer_code` varchar(20) DEFAULT NULL COMMENT '经销商代号',
  `dealer_rate` decimal(5,2) DEFAULT NULL COMMENT '经销商分润比例',
  `dealer_amount` decimal(10,2) DEFAULT NULL COMMENT '经销商分润金额',
  `fee_amount` decimal(10,2) DEFAULT NULL COMMENT '手续费金额',
  `plan_cost` decimal(10,2) DEFAULT NULL COMMENT '套餐成本',
  `profit_amount` decimal(10,2) DEFAULT NULL COMMENT '可分润金额',
  `is_settled` tinyint(1) DEFAULT 0 COMMENT '是否已结算: 0-未结算 1-已结算',
  `online_country` varchar(20) DEFAULT NULL COMMENT '设备上线所属国家',
  `product_type` varchar(32) DEFAULT NULL COMMENT 'cloud_storage 等',
  `product_id` varchar(64) DEFAULT NULL COMMENT '对应套餐ID等',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '金额',
  `currency` varchar(8) DEFAULT 'CNY' COMMENT '币种',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态: pending/paid/cancelled/refunded',
  `payment_method` varchar(32) DEFAULT NULL COMMENT '支付方式 wechat/paypal/apple',
  `third_order_id` varchar(128) DEFAULT NULL COMMENT '三方订单号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `paid_at` datetime DEFAULT NULL COMMENT '支付时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `refund_at` datetime DEFAULT NULL COMMENT '退款时间',
  `refund_reason` varchar(255) DEFAULT NULL COMMENT '退款原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_order` (`order_id`),
  KEY `idx_po_user` (`user_id`),
  KEY `idx_po_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- =====================================================
-- 17. 微信支付扩展表：payment_wechat
-- =====================================================
DROP TABLE IF EXISTS `payment_wechat`;
CREATE TABLE `payment_wechat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) NOT NULL COMMENT '业务订单ID',
  `prepay_id` varchar(128) DEFAULT NULL COMMENT '预支付ID',
  `raw_response` text COMMENT '微信返回的原始内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pw_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信支付扩展表';

-- =====================================================
-- 18. 直播流表：live_stream
-- =====================================================
DROP TABLE IF EXISTS `live_stream`;
CREATE TABLE `live_stream` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stream_id` varchar(64) NOT NULL COMMENT '业务流ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `protocol` varchar(16) DEFAULT NULL COMMENT 'webrtc/hls/rtmp',
  `quality` varchar(16) DEFAULT NULL COMMENT 'sd/hd/fhd',
  `signaling_url` varchar(255) DEFAULT NULL COMMENT '信令地址',
  `ice_servers` text COMMENT 'ICE 服务器 JSON',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ls_stream` (`stream_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播流表';

-- =====================================================
-- 19. WiFi 历史表：wifi_history
-- =====================================================
DROP TABLE IF EXISTS `wifi_history`;
CREATE TABLE `wifi_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `ssid` varchar(64) NOT NULL COMMENT 'WiFi SSID',
  `signal` int DEFAULT NULL COMMENT '信号强度',
  `security` varchar(32) DEFAULT NULL COMMENT '加密方式',
  `is_connected` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否当前连接',
  `last_used_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后使用时间',
  PRIMARY KEY (`id`),
  KEY `idx_wh_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WiFi 历史表';

-- =====================================================
-- 20. 设备绑定表：device_binding
-- =====================================================
DROP TABLE IF EXISTS `device_binding`;
CREATE TABLE `device_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `device_sn` varchar(64) DEFAULT NULL COMMENT '设备序列号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `wifi_ssid` varchar(64) DEFAULT NULL COMMENT 'WiFi 名称',
  `wifi_password` varchar(128) DEFAULT NULL COMMENT 'WiFi 密码',
  `status` varchar(20) DEFAULT 'binding' COMMENT 'binding/success/failed',
  `progress` int DEFAULT 0 COMMENT '绑定进度 0-100',
  `message` varchar(255) DEFAULT NULL COMMENT '提示信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_db_device` (`device_id`),
  KEY `idx_db_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备绑定表';

-- =====================================================
-- 21. App 版本表：app_version
-- =====================================================
DROP TABLE IF EXISTS `app_version`;
CREATE TABLE `app_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform` varchar(16) NOT NULL COMMENT 'ios/android',
  `latest_version` varchar(16) NOT NULL COMMENT '最新版本',
  `min_version` varchar(16) DEFAULT NULL COMMENT '最小支持版本',
  `download_url` varchar(255) DEFAULT NULL COMMENT '下载地址',
  `release_notes` text COMMENT '更新说明',
  `force_update` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否强制更新',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 版本表';

-- =====================================================
-- 22. 用户反馈表：feedback
-- =====================================================
DROP TABLE IF EXISTS `feedback`;
CREATE TABLE `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `feedback_id` varchar(64) NOT NULL COMMENT '业务反馈ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `content` text COMMENT '反馈内容',
  `contact` varchar(128) DEFAULT NULL COMMENT '联系方式',
  `images` text COMMENT '图片URL数组JSON',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_feedback` (`feedback_id`),
  KEY `idx_fb_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';

-- =====================================================
-- 23. 设备分享表：device_share（新表）
-- =====================================================
DROP TABLE IF EXISTS `device_share`;
CREATE TABLE `device_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `share_code` varchar(64) NOT NULL COMMENT '分享码（用于生成二维码）',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `owner_user_id` bigint NOT NULL COMMENT '分享者用户ID',
  `shared_user_id` bigint DEFAULT NULL COMMENT '被分享者用户ID',
  `permission` varchar(20) DEFAULT 'VIEW_ONLY' COMMENT '权限: VIEW_ONLY-仅查看 FULL_CONTROL-完全控制',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING-待使用 USED-已使用 EXPIRED-已过期 REVOKED-已撤销',
  `expire_at` datetime DEFAULT NULL COMMENT '过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ds_code` (`share_code`),
  KEY `idx_ds_device` (`device_id`),
  KEY `idx_ds_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分享表';

-- =====================================================
-- 24. 装机商表：installer（新表）
-- =====================================================
DROP TABLE IF EXISTS `installer`;
CREATE TABLE `installer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `installer_code` varchar(10) NOT NULL COMMENT '装机商代码(1-4位)',
  `installer_name` varchar(100) NOT NULL COMMENT '装机商名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `company_name` varchar(100) DEFAULT NULL COMMENT '公司名称',
  `registered_capital` varchar(50) DEFAULT NULL COMMENT '注册资本(万元)',
  `credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码',
  `registered_address` varchar(255) DEFAULT NULL COMMENT '注册地址',
  `business_license` varchar(500) DEFAULT NULL COMMENT '营业执照图片URL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_installer_code` (`installer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装机商表';

-- =====================================================
-- 25. 经销商表：dealer（新表）
-- =====================================================
DROP TABLE IF EXISTS `dealer`;
CREATE TABLE `dealer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dealer_code` varchar(20) NOT NULL COMMENT '经销商代号',
  `name` varchar(100) NOT NULL COMMENT '经销商名称',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `installer_id` bigint DEFAULT NULL COMMENT '所属装机商ID',
  `installer_code` varchar(10) DEFAULT NULL COMMENT '所属装机商代码',
  `parent_dealer_id` bigint DEFAULT NULL COMMENT '上级经销商ID（NULL表示直属装机商）',
  `level` int DEFAULT 1 COMMENT '层级：1-一级经销商 2-二级经销商...',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `company_name` varchar(100) DEFAULT NULL COMMENT '公司名称',
  `registered_capital` varchar(50) DEFAULT NULL COMMENT '注册资本(万元)',
  `credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码',
  `registered_address` varchar(255) DEFAULT NULL COMMENT '注册地址',
  `business_license` varchar(500) DEFAULT NULL COMMENT '营业执照图片URL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dealer_code` (`dealer_code`),
  KEY `idx_dealer_installer` (`installer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经销商表';

-- =====================================================
-- 26. 设备经销商归属表：device_dealer（新表）
-- =====================================================
DROP TABLE IF EXISTS `device_dealer`;
CREATE TABLE `device_dealer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `installer_id` bigint DEFAULT NULL COMMENT '所属装机商ID',
  `installer_code` varchar(10) DEFAULT NULL COMMENT '装机商代码',
  `dealer_id` bigint DEFAULT NULL COMMENT '当前归属经销商ID',
  `dealer_code` varchar(20) DEFAULT NULL COMMENT '当前归属经销商代号',
  `parent_dealer_id` bigint DEFAULT NULL COMMENT '上级经销商ID',
  `commission_rate` decimal(5,2) DEFAULT NULL COMMENT '分润比例',
  `level` int DEFAULT 1 COMMENT '经销商层级',
  `transfer_id` bigint DEFAULT NULL COMMENT '来源转让记录ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dd_device` (`device_id`),
  KEY `idx_dd_dealer` (`dealer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备经销商归属表';

-- =====================================================
-- 27. 设备转让记录表：device_transfer（新表）
-- =====================================================
DROP TABLE IF EXISTS `device_transfer`;
CREATE TABLE `device_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `transfer_no` varchar(64) NOT NULL COMMENT '转让单号',
  `from_vendor_id` bigint DEFAULT NULL COMMENT '转出经销商ID',
  `from_vendor_code` varchar(20) DEFAULT NULL COMMENT '转出经销商代码',
  `to_vendor_id` bigint NOT NULL COMMENT '转入经销商ID',
  `to_vendor_code` varchar(20) DEFAULT NULL COMMENT '转入经销商代码',
  `commission_rate` decimal(5,2) DEFAULT NULL COMMENT '分润比例',
  `device_count` int DEFAULT 0 COMMENT '转让设备数量',
  `device_ids` text COMMENT '设备ID列表（JSON数组）',
  `installer_id` bigint DEFAULT NULL COMMENT '所属装机商ID',
  `installer_code` varchar(10) DEFAULT NULL COMMENT '所属装机商代码',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING-待确认 COMPLETED-已完成 CANCELLED-已取消',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dt_no` (`transfer_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备转让记录表';

-- =====================================================
-- 28. 套餐分润配置表：plan_commission（新表）
-- =====================================================
DROP TABLE IF EXISTS `plan_commission`;
CREATE TABLE `plan_commission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` varchar(64) NOT NULL COMMENT '套餐ID',
  `payee_entity` varchar(50) DEFAULT NULL COMMENT '收款主体名称',
  `fee_type` varchar(20) DEFAULT 'FIXED' COMMENT '手续费类型: FIXED-固定比例 MIXED-混合',
  `fee_rate` decimal(5,2) DEFAULT NULL COMMENT '手续费比例(%)',
  `fee_fixed` decimal(10,2) DEFAULT NULL COMMENT '固定手续费金额',
  `rebate_rate` decimal(5,2) DEFAULT NULL COMMENT '套餐返点(%)',
  `plan_cost` decimal(10,2) DEFAULT NULL COMMENT '套餐成本',
  `profit_mode` varchar(20) DEFAULT 'PROFIT' COMMENT '分润模式: PROFIT-按利润 REVENUE-按营收',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_pc_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐分润配置表';

-- =====================================================
-- 29. 系统字典表：sys_dict（新表）
-- =====================================================
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category` varchar(50) NOT NULL COMMENT '分类',
  `code` varchar(50) NOT NULL COMMENT '字典代码',
  `name` varchar(100) NOT NULL COMMENT '显示名称',
  `sort_order` int DEFAULT 0 COMMENT '排序',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sd_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典表';

-- =====================================================
-- 30. 设备生产批次表：device_production_batch（新表）
-- =====================================================
DROP TABLE IF EXISTS `device_production_batch`;
CREATE TABLE `device_production_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` varchar(64) NOT NULL COMMENT '批次号',
  `network_lens` varchar(10) DEFAULT NULL COMMENT '网络+镜头配置(第1-2位)',
  `device_form` varchar(10) DEFAULT NULL COMMENT '设备形态(第3位)',
  `special_req` varchar(10) DEFAULT NULL COMMENT '特殊要求(第4位)',
  `assembler_code` varchar(10) DEFAULT NULL COMMENT '装机商代码(第5位)',
  `vendor_code` varchar(10) DEFAULT NULL COMMENT '销售商代码(第6-7位)',
  `reserved` varchar(10) DEFAULT NULL COMMENT '预留位(第8位)',
  `quantity` int DEFAULT 0 COMMENT '生产数量',
  `start_serial` int DEFAULT NULL COMMENT '起始序列号',
  `end_serial` int DEFAULT NULL COMMENT '结束序列号',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING-待生产 PRODUCING-生产中 COMPLETED-已完成',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `installer_commission_rate` decimal(5,2) DEFAULT NULL COMMENT '装机商分润比例(%)',
  `dealer_commission_rate` decimal(5,2) DEFAULT NULL COMMENT '经销商分润比例(%)',
  `enable_ad` tinyint(1) DEFAULT 0 COMMENT '是否开启广告',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dpb_batch` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备生产批次表';

-- =====================================================
-- 31. 生产设备表：manufactured_device（新表）
-- =====================================================
DROP TABLE IF EXISTS `manufactured_device`;
CREATE TABLE `manufactured_device` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '完整设备ID(16位)',
  `batch_id` bigint DEFAULT NULL COMMENT '关联批次ID',
  `network_lens` varchar(10) DEFAULT NULL COMMENT '网络+镜头(第1-2位)',
  `device_form` varchar(10) DEFAULT NULL COMMENT '设备形态(第3位)',
  `special_req` varchar(10) DEFAULT NULL COMMENT '特殊要求(第4位)',
  `assembler_code` varchar(10) DEFAULT NULL COMMENT '装机商代码(第5位)',
  `vendor_code` varchar(10) DEFAULT NULL COMMENT '销售商代码(第6-7位)',
  `installer_id` bigint DEFAULT NULL COMMENT '装机商ID',
  `current_dealer_id` bigint DEFAULT NULL COMMENT '当前所属经销商ID',
  `installer_commission_rate` decimal(5,2) DEFAULT NULL COMMENT '装机商分润比例(%)',
  `dealer_commission_rate` decimal(5,2) DEFAULT NULL COMMENT '经销商分润比例(%)',
  `enable_ad` tinyint(1) DEFAULT 0 COMMENT '是否开启广告',
  `serial_no` varchar(20) DEFAULT NULL COMMENT '序列号(第9-16位)',
  `mac_address` varchar(20) DEFAULT NULL COMMENT 'MAC地址',
  `status` varchar(20) DEFAULT 'MANUFACTURED' COMMENT '状态: MANUFACTURED-已生产 ACTIVATED-已激活 BOUND-已绑定',
  `manufactured_at` datetime DEFAULT NULL COMMENT '生产时间',
  `activated_at` datetime DEFAULT NULL COMMENT '激活时间',
  `country` varchar(20) DEFAULT NULL COMMENT '上线所属国家',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md_device` (`device_id`),
  KEY `idx_md_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产设备表';

-- =====================================================
-- 32. 装机商表：assembler（新表）
-- =====================================================
DROP TABLE IF EXISTS `assembler`;
CREATE TABLE `assembler` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `assembler_code` varchar(10) NOT NULL COMMENT '装机商代码(1位)',
  `assembler_name` varchar(100) NOT NULL COMMENT '装机商名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assembler_code` (`assembler_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装机商表';

-- =====================================================
-- 33. 销售商表：vendor（新表）
-- =====================================================
DROP TABLE IF EXISTS `vendor`;
CREATE TABLE `vendor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vendor_code` varchar(10) NOT NULL COMMENT '销售商代码(2位)',
  `vendor_name` varchar(100) NOT NULL COMMENT '销售商名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `installer_id` bigint DEFAULT NULL COMMENT '所属装机商ID',
  `installer_code` varchar(10) DEFAULT NULL COMMENT '所属装机商代码',
  `parent_vendor_id` bigint DEFAULT NULL COMMENT '上级经销商ID',
  `level` int DEFAULT 1 COMMENT '层级：1-一级经销商 2-二级...',
  `commission_rate` decimal(5,2) DEFAULT NULL COMMENT '分佣比例(%)',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vendor_code` (`vendor_code`),
  KEY `idx_vendor_installer` (`installer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售商表';

-- =====================================================
-- 34. App日志表：app_log（新表）
-- =====================================================
DROP TABLE IF EXISTS `app_log`;
CREATE TABLE `app_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `device_id` varchar(100) DEFAULT NULL COMMENT '设备ID(手机设备标识)',
  `app_version` varchar(20) DEFAULT NULL COMMENT 'App版本',
  `device_model` varchar(50) DEFAULT NULL COMMENT '设备型号',
  `os_version` varchar(20) DEFAULT NULL COMMENT '操作系统版本',
  `level` varchar(20) DEFAULT 'info' COMMENT '日志级别: debug/info/warning/error',
  `tag` varchar(50) DEFAULT NULL COMMENT '日志标签',
  `message` text COMMENT '日志消息',
  `extra` text COMMENT '额外数据(JSON)',
  `client_time` datetime DEFAULT NULL COMMENT '客户端时间戳',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务器接收时间',
  PRIMARY KEY (`id`),
  KEY `idx_al_user` (`user_id`),
  KEY `idx_al_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App日志表';

-- =====================================================
-- 35. 用户操作日志表：user_operation_log（新表）
-- =====================================================
DROP TABLE IF EXISTS `user_operation_log`;
CREATE TABLE `user_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(64) DEFAULT NULL COMMENT '用户名/手机号',
  `module` varchar(50) DEFAULT NULL COMMENT '操作模块: device/user/cloud/payment/share/setting',
  `action` varchar(50) DEFAULT NULL COMMENT '操作类型: view/add/edit/delete/bind/unbind/share/buy',
  `description` varchar(255) DEFAULT NULL COMMENT '操作描述',
  `target_id` varchar(100) DEFAULT NULL COMMENT '操作目标ID',
  `target_type` varchar(50) DEFAULT NULL COMMENT '操作目标类型: device/order/plan/share',
  `request_params` text COMMENT '请求参数(JSON)',
  `response_result` text COMMENT '响应结果(JSON)',
  `result` varchar(20) DEFAULT NULL COMMENT '操作结果: success/fail',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '客户端IP地址',
  `device_type` varchar(20) DEFAULT NULL COMMENT '客户端设备类型: android/ios/web',
  `device_model` varchar(50) DEFAULT NULL COMMENT '客户端设备型号',
  `app_version` varchar(20) DEFAULT NULL COMMENT 'App版本',
  `os_version` varchar(20) DEFAULT NULL COMMENT '操作系统版本',
  `cost_time` bigint DEFAULT NULL COMMENT '请求耗时(毫秒)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_uol_user` (`user_id`),
  KEY `idx_uol_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户操作日志表';

-- =====================================================
-- 36. 设备经销商归属表：device_vendor（新表）
-- =====================================================
DROP TABLE IF EXISTS `device_vendor`;
CREATE TABLE `device_vendor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `installer_id` bigint DEFAULT NULL COMMENT '所属装机商ID',
  `installer_code` varchar(10) DEFAULT NULL COMMENT '装机商代码',
  `vendor_id` bigint DEFAULT NULL COMMENT '当前归属经销商ID',
  `vendor_code` varchar(20) DEFAULT NULL COMMENT '当前归属经销商代码',
  `parent_vendor_id` bigint DEFAULT NULL COMMENT '上级经销商ID',
  `commission_rate` decimal(5,2) DEFAULT NULL COMMENT '分润比例',
  `level` int DEFAULT 1 COMMENT '经销商层级',
  `transfer_id` bigint DEFAULT NULL COMMENT '来源转让记录ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dv_device` (`device_id`),
  KEY `idx_dv_vendor` (`vendor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备经销商归属表';

-- =====================================================
-- 37. 设备状态历史表：device_status_history（预留）
-- =====================================================
DROP TABLE IF EXISTS `device_status_history`;
CREATE TABLE `device_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `status` tinyint(1) NOT NULL COMMENT '设备状态 0-离线 1-在线',
  `reason` varchar(100) DEFAULT NULL COMMENT '变化原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_dsh_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备状态历史表（预留）';

-- =====================================================
-- 38. WebRTC会话表：webrtc_session（预留）
-- =====================================================
DROP TABLE IF EXISTS `webrtc_session`;
CREATE TABLE `webrtc_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(50) NOT NULL COMMENT '设备ID',
  `sid` varchar(100) NOT NULL COMMENT '会话ID/Peer ID',
  `client_id` varchar(100) DEFAULT NULL COMMENT '客户端标识',
  `status` varchar(20) DEFAULT NULL COMMENT '会话状态 created/connected/closed',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ws_device` (`device_id`),
  KEY `idx_ws_sid` (`sid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebRTC会话表（预留）';

-- =====================================================
-- 39. 业务员表：salesman（新表）
-- =====================================================
DROP TABLE IF EXISTS `salesman`;
CREATE TABLE `salesman` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vendor_id` bigint DEFAULT NULL COMMENT '所属经销商ID',
  `vendor_code` varchar(20) DEFAULT NULL COMMENT '所属经销商代码',
  `name` varchar(50) NOT NULL COMMENT '业务员姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `commission_rate` decimal(5,2) DEFAULT NULL COMMENT '佣金比例(0-100)',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sm_vendor` (`vendor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务员表';

SET FOREIGN_KEY_CHECKS = 1;

-- 执行完成提示
SELECT '建表脚本执行完成！共创建 39 张表。' AS message;
