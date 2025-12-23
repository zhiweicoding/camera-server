-- 业务员管理 + 字典管理 + 账单统计 数据库变更
-- 在 camera_app 数据库下执行

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 新增表：业务员表 salesman
DROP TABLE IF EXISTS `salesman`;
CREATE TABLE `salesman` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vendor_id`       BIGINT       NOT NULL COMMENT '所属经销商ID',
  `vendor_code`     VARCHAR(2)   NOT NULL COMMENT '所属经销商代码',
  `name`            VARCHAR(50)  NOT NULL COMMENT '业务员姓名',
  `phone`           VARCHAR(20)           DEFAULT NULL COMMENT '联系电话',
  `commission_rate` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '佣金比例(0-100)',
  `status`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
  `remark`          VARCHAR(255)          DEFAULT NULL COMMENT '备注',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_vendor_id` (`vendor_id`),
  KEY `idx_vendor_code` (`vendor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务员表';

-- 2. 新增表：系统字典表 sys_dict
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category`      VARCHAR(32)  NOT NULL COMMENT '分类: network_lens/device_form/special_req/reserved/assembler_code',
  `code`          VARCHAR(10)  NOT NULL COMMENT '字典代码',
  `name`          VARCHAR(50)           DEFAULT NULL COMMENT '显示名称',
  `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category`, `code`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典表';

-- 初始化字典数据
INSERT INTO `sys_dict` (`category`, `code`, `name`, `sort_order`, `status`) VALUES
-- 网络+镜头配置 (第1-2位)
('network_lens', 'A1', 'A1', 1, 1),
('network_lens', 'A2', 'A2', 2, 1),
('network_lens', 'A3', 'A3', 3, 1),
('network_lens', 'B1', 'B1', 4, 1),
('network_lens', 'B2', 'B2', 5, 1),
('network_lens', 'B3', 'B3', 6, 1),
('network_lens', 'C1', 'C1', 7, 1),
('network_lens', 'C2', 'C2', 8, 1),
('network_lens', 'R1', 'R1', 9, 1),
-- 设备形态 (第3位)
('device_form', '1', '形态1', 1, 1),
('device_form', '2', '形态2', 2, 1),
('device_form', '3', '形态3', 3, 1),
('device_form', '4', '形态4', 4, 1),
('device_form', '5', '形态5', 5, 1),
-- 特殊要求 (第4位)
('special_req', '0', '无特殊要求', 1, 1),
('special_req', '1', '特殊要求1', 2, 1),
('special_req', '2', '特殊要求2', 3, 1),
('special_req', '3', '特殊要求3', 4, 1),
-- 预留位 (第8位)
('reserved', '0', '预留0', 1, 1),
-- 装机商代码 (第5位)
('assembler_code', '0', '默认(组装厂=客户)', 1, 1),
('assembler_code', 'A', '测试账号', 2, 1);

-- 3. 修改表：manufactured_device 增加业务员字段
ALTER TABLE `manufactured_device` ADD COLUMN `salesman_id` BIGINT DEFAULT NULL COMMENT '业务员ID' AFTER `vendor_code`;
ALTER TABLE `manufactured_device` ADD KEY `idx_salesman_id` (`salesman_id`);

-- 4. 修改表：payment_order 增加快照字段
ALTER TABLE `payment_order` ADD COLUMN `vendor_code` VARCHAR(2) DEFAULT NULL COMMENT '经销商代码' AFTER `device_id`;
ALTER TABLE `payment_order` ADD COLUMN `salesman_id` BIGINT DEFAULT NULL COMMENT '业务员ID' AFTER `vendor_code`;
ALTER TABLE `payment_order` ADD COLUMN `salesman_name` VARCHAR(50) DEFAULT NULL COMMENT '业务员姓名(快照)' AFTER `salesman_id`;
ALTER TABLE `payment_order` ADD COLUMN `commission_rate` DECIMAL(5,2) DEFAULT NULL COMMENT '佣金比例(快照)' AFTER `salesman_name`;
ALTER TABLE `payment_order` ADD COLUMN `salesman_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '业务员应得金额' AFTER `commission_rate`;
ALTER TABLE `payment_order` ADD COLUMN `vendor_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '经销商应得金额' AFTER `salesman_amount`;
ALTER TABLE `payment_order` ADD COLUMN `refund_at` DATETIME DEFAULT NULL COMMENT '退款时间' AFTER `paid_at`;
ALTER TABLE `payment_order` ADD COLUMN `refund_reason` VARCHAR(255) DEFAULT NULL COMMENT '退款原因' AFTER `refund_at`;
ALTER TABLE `payment_order` ADD KEY `idx_vendor_code` (`vendor_code`);
ALTER TABLE `payment_order` ADD KEY `idx_salesman_id` (`salesman_id`);

-- 5. 修改表：cloud_plan 增加管理字段（如果字段不存在才添加）
-- 注意：执行前请检查是否已存在这些字段
ALTER TABLE `cloud_plan` ADD COLUMN `type` VARCHAR(20) DEFAULT 'motion' COMMENT '套餐类型: motion/fulltime/traffic' AFTER `features`;
ALTER TABLE `cloud_plan` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT '排序' AFTER `type`;
ALTER TABLE `cloud_plan` ADD COLUMN `status` TINYINT(1) DEFAULT 1 COMMENT '状态 0-下架 1-上架' AFTER `sort_order`;
ALTER TABLE `cloud_plan` ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `status`;
ALTER TABLE `cloud_plan` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `created_at`;

SET FOREIGN_KEY_CHECKS = 1;
