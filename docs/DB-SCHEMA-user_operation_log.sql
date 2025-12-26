-- 用户操作日志表建表脚本
-- 用于记录用户在 APP 中的操作行为

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户操作日志表：user_operation_log
DROP TABLE IF EXISTS `user_operation_log`;
CREATE TABLE `user_operation_log` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`         bigint                DEFAULT NULL COMMENT '用户ID',
  `username`        varchar(64)           DEFAULT NULL COMMENT '用户名/手机号（冗余存储，便于查询）',
  `module`          varchar(32)  NOT NULL COMMENT '操作模块: device-设备管理, user-用户中心, cloud-云存储, payment-支付, share-分享, setting-设置',
  `action`          varchar(32)  NOT NULL COMMENT '操作类型: view-查看, add-添加, edit-修改, delete-删除, bind-绑定, unbind-解绑, share-分享, buy-购买',
  `description`     varchar(255)          DEFAULT NULL COMMENT '操作描述',
  `target_id`       varchar(64)           DEFAULT NULL COMMENT '操作目标ID（如设备ID、订单ID等）',
  `target_type`     varchar(32)           DEFAULT NULL COMMENT '操作目标类型: device-设备, order-订单, plan-套餐, share-分享',
  `request_params`  text                  COMMENT '请求参数 (JSON格式)',
  `response_result` text                  COMMENT '响应结果 (JSON格式，可选)',
  `result`          varchar(16)           DEFAULT NULL COMMENT '操作结果: success-成功, fail-失败',
  `error_message`   varchar(500)          DEFAULT NULL COMMENT '错误信息（失败时记录）',
  `ip_address`      varchar(64)           DEFAULT NULL COMMENT '客户端IP地址',
  `device_type`     varchar(20)           DEFAULT NULL COMMENT '客户端设备类型: android, ios, web',
  `device_model`    varchar(100)          DEFAULT NULL COMMENT '客户端设备型号',
  `app_version`     varchar(32)           DEFAULT NULL COMMENT 'App版本',
  `os_version`      varchar(32)           DEFAULT NULL COMMENT '操作系统版本',
  `cost_time`       bigint                DEFAULT NULL COMMENT '请求耗时（毫秒）',
  `created_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_uol_user_id` (`user_id`),
  KEY `idx_uol_module` (`module`),
  KEY `idx_uol_action` (`action`),
  KEY `idx_uol_target` (`target_type`, `target_id`),
  KEY `idx_uol_result` (`result`),
  KEY `idx_uol_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户操作日志表';

SET FOREIGN_KEY_CHECKS = 1;

-- ================== 使用示例 ==================
-- 
-- 1. 简单记录方式：
-- INSERT INTO user_operation_log (user_id, username, module, action, description, target_type, target_id, result, ip_address, device_type)
-- VALUES (1, '13800138000', 'device', 'bind', '绑定设备', 'device', 'A111-abc123', 'success', '192.168.1.100', 'android');
--
-- 2. 查询某用户的操作记录：
-- SELECT * FROM user_operation_log WHERE user_id = 1 ORDER BY created_at DESC LIMIT 20;
--
-- 3. 查询某设备相关的操作记录：
-- SELECT * FROM user_operation_log WHERE target_type = 'device' AND target_id = 'A111-abc123' ORDER BY created_at DESC;
--
-- 4. 查询失败的操作：
-- SELECT * FROM user_operation_log WHERE result = 'fail' ORDER BY created_at DESC;
--
-- 5. 统计各模块操作次数：
-- SELECT module, action, COUNT(*) as cnt FROM user_operation_log GROUP BY module, action ORDER BY cnt DESC;
