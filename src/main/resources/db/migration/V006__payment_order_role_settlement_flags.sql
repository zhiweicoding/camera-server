-- Add independent settlement flags for installer/dealer settlements.
ALTER TABLE `payment_order`
    ADD COLUMN IF NOT EXISTS `installer_is_settled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '装机商是否已结算：0-未结算 1-已结算' AFTER `is_settled`,
    ADD COLUMN IF NOT EXISTS `dealer_is_settled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '经销商是否已结算：0-未结算 1-已结算' AFTER `installer_is_settled`;

-- Backfill new flags from legacy column.
UPDATE `payment_order`
SET `installer_is_settled` = IFNULL(`installer_is_settled`, IFNULL(`is_settled`, 0)),
    `dealer_is_settled` = IFNULL(`dealer_is_settled`, IFNULL(`is_settled`, 0));

-- Keep legacy flag as "both dimensions settled".
UPDATE `payment_order`
SET `is_settled` = CASE
    WHEN IFNULL(`installer_is_settled`, 0) = 1 AND IFNULL(`dealer_is_settled`, 0) = 1 THEN 1
    ELSE 0
END;
