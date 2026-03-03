-- 设备与4G流量SIM映射表
CREATE TABLE IF NOT EXISTS `device_traffic_sim` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `device_id` VARCHAR(50) NOT NULL COMMENT '设备ID',
    `sim_id` VARCHAR(64) NOT NULL COMMENT 'SIM卡标识(SIM-ID/ICCID)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_device_traffic_sim_device` (`device_id`),
    INDEX `idx_device_traffic_sim_sim` (`sim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备4G流量SIM映射表';

