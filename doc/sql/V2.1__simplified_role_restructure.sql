-- ============================================================
-- V2.1 简化版角色重构
-- 核心变更：
--   1. 保留 vendor(经销商) 表，增加装机商关联
--   2. 删除 salesman(业务员) 相关
--   3. 新增 installer(装机商) 表
--   4. 新增设备转让功能
-- ============================================================

-- ============================================================
-- 第一部分：创建装机商表
-- ============================================================

CREATE TABLE IF NOT EXISTS installer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    installer_code VARCHAR(4) NOT NULL COMMENT '装机商代码(1-4位)',
    installer_name VARCHAR(100) NOT NULL COMMENT '装机商名称',
    contact_person VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(200) COMMENT '地址',
    commission_rate DECIMAL(5,2) DEFAULT 0 COMMENT '分佣比例(基于可分润金额的百分比)',
    status VARCHAR(20) DEFAULT 'ENABLED' COMMENT '状态: DISABLED-禁用, ENABLED-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_installer_code (installer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装机商表';

-- ============================================================
-- 第二部分：修改 vendor(经销商) 表，增加装机商关联
-- ============================================================

ALTER TABLE vendor
    ADD COLUMN IF NOT EXISTS installer_id BIGINT COMMENT '所属装机商ID',
    ADD COLUMN IF NOT EXISTS installer_code VARCHAR(4) COMMENT '所属装机商代码',
    ADD COLUMN IF NOT EXISTS commission_rate DECIMAL(5,2) DEFAULT 0 COMMENT '分佣比例(基于装机商利润的百分比)',
    ADD COLUMN IF NOT EXISTS parent_vendor_id BIGINT COMMENT '上级经销商ID（支持多级）',
    ADD COLUMN IF NOT EXISTS level INT DEFAULT 1 COMMENT '层级：1-一级经销商, 2-二级...';

CREATE INDEX IF NOT EXISTS idx_vendor_installer ON vendor(installer_id);
CREATE INDEX IF NOT EXISTS idx_vendor_parent ON vendor(parent_vendor_id);

-- ============================================================
-- 第三部分：创建设备转让记录表
-- ============================================================

CREATE TABLE IF NOT EXISTS device_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_no VARCHAR(32) NOT NULL COMMENT '转让单号',
    
    -- 转出方
    from_vendor_id BIGINT COMMENT '转出经销商ID（NULL表示装机商直接分配）',
    from_vendor_code VARCHAR(10) COMMENT '转出经销商代码',
    
    -- 转入方
    to_vendor_id BIGINT NOT NULL COMMENT '转入经销商ID',
    to_vendor_code VARCHAR(10) COMMENT '转入经销商代码',
    
    -- 分润配置
    commission_rate DECIMAL(5,2) NOT NULL COMMENT '分润比例（基于上级利润的百分比）',
    
    -- 设备信息
    device_count INT NOT NULL DEFAULT 0 COMMENT '转让设备数量',
    device_ids TEXT COMMENT '设备ID列表（JSON数组）',
    
    -- 装机商信息
    installer_id BIGINT COMMENT '所属装机商ID',
    installer_code VARCHAR(4) COMMENT '所属装机商代码',
    
    -- 状态
    status VARCHAR(20) DEFAULT 'COMPLETED' COMMENT '状态：PENDING-待确认, COMPLETED-已完成, CANCELLED-已取消',
    
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_transfer_no (transfer_no),
    INDEX idx_from_vendor (from_vendor_id),
    INDEX idx_to_vendor (to_vendor_id),
    INDEX idx_installer (installer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备转让记录表';

-- ============================================================
-- 第四部分：创建设备经销商归属表（支持多级）
-- ============================================================

CREATE TABLE IF NOT EXISTS device_vendor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(20) NOT NULL COMMENT '设备ID',
    
    -- 装机商信息
    installer_id BIGINT COMMENT '所属装机商ID',
    installer_code VARCHAR(4) COMMENT '装机商代码',
    
    -- 当前经销商
    vendor_id BIGINT NOT NULL COMMENT '当前归属经销商ID',
    vendor_code VARCHAR(10) COMMENT '当前归属经销商代码',
    
    -- 上级经销商
    parent_vendor_id BIGINT COMMENT '上级经销商ID（NULL表示直接从装机商获得）',
    
    -- 分润比例
    commission_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '分润比例（基于上级利润）',
    
    -- 层级
    level INT DEFAULT 1 COMMENT '经销商层级：1-一级, 2-二级...',
    
    -- 转让来源
    transfer_id BIGINT COMMENT '来源转让记录ID（NULL表示初始分配）',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_device_vendor (device_id, vendor_id),
    INDEX idx_device (device_id),
    INDEX idx_vendor (vendor_id),
    INDEX idx_parent (parent_vendor_id),
    INDEX idx_installer (installer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备经销商归属表';

-- ============================================================
-- 第五部分：修改 manufactured_device 表
-- ============================================================

ALTER TABLE manufactured_device
    ADD COLUMN IF NOT EXISTS installer_id BIGINT COMMENT '装机商ID',
    ADD COLUMN IF NOT EXISTS current_vendor_id BIGINT COMMENT '当前所属经销商ID';

-- 迁移原 vendor_code 数据到 installer
-- 注意：这里假设原来的 vendor 就是装机商，需要根据实际业务调整
-- UPDATE manufactured_device md 
-- SET installer_id = (SELECT id FROM installer WHERE installer_code = md.assembler_code);

CREATE INDEX IF NOT EXISTS idx_md_installer ON manufactured_device(installer_id);
CREATE INDEX IF NOT EXISTS idx_md_current_vendor ON manufactured_device(current_vendor_id);

-- ============================================================
-- 第六部分：修改 payment_order 表
-- ============================================================

ALTER TABLE payment_order
    ADD COLUMN  installer_id BIGINT COMMENT '装机商ID',
    ADD COLUMN  installer_code VARCHAR(4) COMMENT '装机商代码',
    ADD COLUMN  installer_rate DECIMAL(5,2) COMMENT '装机商分润比例（快照）',
    ADD COLUMN  installer_amount DECIMAL(10,2) COMMENT '装机商分润金额',
    ADD COLUMN  vendor_rate DECIMAL(5,2) COMMENT '经销商分润比例（快照）',
    ADD COLUMN  fee_amount DECIMAL(10,2) COMMENT '手续费金额',
    ADD COLUMN  plan_cost DECIMAL(10,2) COMMENT '套餐成本',
    ADD COLUMN  profit_amount DECIMAL(10,2) COMMENT '可分润金额',
    ADD COLUMN  is_settled TINYINT DEFAULT 0 COMMENT '是否已结算：0-未结算 1-已结算',
    ADD COLUMN  online_country VARCHAR(50) COMMENT '设备上线所属国家';

CREATE INDEX  idx_po_installer ON payment_order(installer_id);
CREATE INDEX  idx_po_settled ON payment_order(is_settled);

-- ============================================================
-- 第七部分：修改 cloud_plan 表
-- ============================================================

ALTER TABLE cloud_plan
    ADD COLUMN IF NOT EXISTS plan_cost DECIMAL(10,2) DEFAULT 0 COMMENT '套餐成本';

-- 同步套餐成本
UPDATE cloud_plan cp
INNER JOIN plan_commission pc ON cp.plan_id = pc.plan_id
SET cp.plan_cost = pc.plan_cost
WHERE cp.plan_cost IS NULL OR cp.plan_cost = 0;

-- ============================================================
-- 第八部分：修改 user 表
-- ============================================================

ALTER TABLE user
    ADD COLUMN IF NOT EXISTS installer_id BIGINT COMMENT '关联装机商ID',
    ADD COLUMN IF NOT EXISTS dealer_id BIGINT COMMENT '关联经销商ID',
    ADD COLUMN IF NOT EXISTS is_installer TINYINT DEFAULT 0 COMMENT '是否为装机商身份: 0-否 1-是',
    ADD COLUMN IF NOT EXISTS is_dealer TINYINT DEFAULT 0 COMMENT '是否为经销商身份: 0-否 1-是';

-- 双重身份索引
CREATE INDEX IF NOT EXISTS idx_user_installer ON user(installer_id);
CREATE INDEX IF NOT EXISTS idx_user_dealer ON user(dealer_id);
CREATE INDEX IF NOT EXISTS idx_user_is_installer ON user(is_installer);
CREATE INDEX IF NOT EXISTS idx_user_is_dealer ON user(is_dealer);

-- user.role 说明: 1-流通用户 2-经销商 3-管理员 4-装机商
-- is_installer/is_dealer 支持双重身份，可以同时为1

-- ============================================================
-- 第九部分：可选 - 删除业务员相关（建议先备份）
-- ============================================================

-- 删除业务员相关字段
-- ALTER TABLE manufactured_device DROP COLUMN IF EXISTS salesman_id;
-- ALTER TABLE payment_order DROP COLUMN IF EXISTS salesman_id;
-- ALTER TABLE payment_order DROP COLUMN IF EXISTS salesman_name;
-- ALTER TABLE payment_order DROP COLUMN IF EXISTS salesman_amount;

-- 删除业务员表
-- DROP TABLE IF EXISTS salesman;
