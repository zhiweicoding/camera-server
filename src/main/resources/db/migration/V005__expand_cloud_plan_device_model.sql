-- Expand cloud_plan.device_model for multi-model values (comma-separated codes)
ALTER TABLE `cloud_plan`
    ADD COLUMN IF NOT EXISTS `device_model` VARCHAR(255) COMMENT 'Model codes (network_lens), comma-separated';

ALTER TABLE `cloud_plan`
    MODIFY COLUMN `device_model` VARCHAR(255) COMMENT 'Model codes (network_lens), comma-separated';
