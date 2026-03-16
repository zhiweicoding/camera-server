ALTER TABLE `user_push_token`
    ADD COLUMN `provider` VARCHAR(20) NULL COMMENT '推送提供方: jpush/fcm' AFTER `registration_id`,
    ADD COLUMN `channel` VARCHAR(20) NULL COMMENT '推送通道: jpush/fcm' AFTER `provider`;

UPDATE `user_push_token`
SET `provider` = 'jpush'
WHERE `provider` IS NULL OR TRIM(`provider`) = '';

UPDATE `user_push_token`
SET `channel` = `provider`
WHERE `channel` IS NULL OR TRIM(`channel`) = '';

ALTER TABLE `user_push_token`
    MODIFY COLUMN `provider` VARCHAR(20) NOT NULL COMMENT '推送提供方: jpush/fcm',
    MODIFY COLUMN `channel` VARCHAR(20) NOT NULL COMMENT '推送通道: jpush/fcm';

DROP INDEX `uk_user_registration` ON `user_push_token`;

CREATE UNIQUE INDEX `uk_user_registration_provider`
    ON `user_push_token` (`user_id`, `registration_id`, `provider`);

CREATE INDEX `idx_provider` ON `user_push_token` (`provider`);
CREATE INDEX `idx_channel` ON `user_push_token` (`channel`);
