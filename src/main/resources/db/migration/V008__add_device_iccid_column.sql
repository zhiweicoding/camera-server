ALTER TABLE `device`
    ADD COLUMN IF NOT EXISTS `iccid` VARCHAR(64) DEFAULT NULL COMMENT '4G SIM ICCID';

CREATE INDEX `idx_device_iccid` ON `device` (`iccid`);
