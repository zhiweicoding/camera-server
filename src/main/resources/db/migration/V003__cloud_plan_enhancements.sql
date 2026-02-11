-- 云存储套餐表增强：新增机型、周期月数、流量、语言、自动续费等字段
ALTER TABLE `cloud_plan`
    ADD COLUMN IF NOT EXISTS `device_model` VARCHAR(20) COMMENT '机型代码(关联network_lens)',
    ADD COLUMN IF NOT EXISTS `period_num` INT DEFAULT 1 COMMENT '周期月数: 1/3/12',
    ADD COLUMN IF NOT EXISTS `traffic_gb` INT COMMENT '流量(GB), 仅4G流量类型使用',
    ADD COLUMN IF NOT EXISTS `language` VARCHAR(20) COMMENT '语言设置: zh/en等',
    ADD COLUMN IF NOT EXISTS `auto_renew` TINYINT DEFAULT 0 COMMENT '是否自动续费: 0-否 1-是',
    ADD COLUMN IF NOT EXISTS `plan_cost` DECIMAL(10,2) COMMENT '套餐成本',
    ADD COLUMN IF NOT EXISTS `type` VARCHAR(20) COMMENT '套餐类型',
    ADD COLUMN IF NOT EXISTS `sort_order` INT DEFAULT 0 COMMENT '排序序号',
    ADD COLUMN IF NOT EXISTS `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用';
