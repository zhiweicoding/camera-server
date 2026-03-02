-- Cloud plan enhancements: add model, period number, traffic, language, auto-renew, cost, type, sort and status fields
ALTER TABLE `cloud_plan`
    ADD COLUMN IF NOT EXISTS `device_model` VARCHAR(255) COMMENT 'Model codes (network_lens), comma-separated',
    ADD COLUMN IF NOT EXISTS `period_num` INT DEFAULT 1 COMMENT 'Billing period month count: 1/3/12',
    ADD COLUMN IF NOT EXISTS `traffic_gb` INT COMMENT 'Traffic (GB), used by traffic plans',
    ADD COLUMN IF NOT EXISTS `language` VARCHAR(20) COMMENT 'Language: zh/en',
    ADD COLUMN IF NOT EXISTS `auto_renew` TINYINT DEFAULT 0 COMMENT 'Auto renew: 0-no 1-yes',
    ADD COLUMN IF NOT EXISTS `plan_cost` DECIMAL(10,2) COMMENT 'Plan cost',
    ADD COLUMN IF NOT EXISTS `type` VARCHAR(20) COMMENT 'Plan type',
    ADD COLUMN IF NOT EXISTS `sort_order` INT DEFAULT 0 COMMENT 'Sort order',
    ADD COLUMN IF NOT EXISTS `status` TINYINT DEFAULT 1 COMMENT 'Status: 0-disabled 1-enabled';
