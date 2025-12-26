-- ============================================
-- 设备状态字段扩展
-- 用于存储摄像头上报的实时状态信息
-- ============================================

-- WiFi信号强度
ALTER TABLE device ADD COLUMN IF NOT EXISTS wifi_rssi INT DEFAULT NULL COMMENT 'WiFi信号强度(RSSI)';

-- TF卡相关字段
ALTER TABLE device ADD COLUMN IF NOT EXISTS sd_state INT DEFAULT NULL COMMENT 'TF卡状态: 0-无 1-有';
ALTER TABLE device ADD COLUMN IF NOT EXISTS sd_capacity BIGINT DEFAULT NULL COMMENT 'TF卡总块数';
ALTER TABLE device ADD COLUMN IF NOT EXISTS sd_block_size BIGINT DEFAULT NULL COMMENT 'TF卡块大小(字节)';
ALTER TABLE device ADD COLUMN IF NOT EXISTS sd_free BIGINT DEFAULT NULL COMMENT 'TF卡空闲块数';

-- 摄像头设置状态字段
ALTER TABLE device ADD COLUMN IF NOT EXISTS rotate INT DEFAULT 0 COMMENT '画面旋转: 0-正常 1-旋转180度';
ALTER TABLE device ADD COLUMN IF NOT EXISTS light_led INT DEFAULT 0 COMMENT '照明灯状态: 0-关闭 1-开启';
ALTER TABLE device ADD COLUMN IF NOT EXISTS white_led INT DEFAULT 0 COMMENT '白光灯状态: 0-禁用 1-启用';

-- 心跳检测字段
ALTER TABLE device ADD COLUMN IF NOT EXISTS last_heartbeat_time DATETIME DEFAULT NULL COMMENT '最后心跳时间(用于离线检测)';

-- 添加索引优化查询
CREATE INDEX IF NOT EXISTS idx_device_status ON device(status);
CREATE INDEX IF NOT EXISTS idx_device_last_heartbeat ON device(last_heartbeat_time);
