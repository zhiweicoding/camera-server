ALTER TABLE `cloud_plan`
    ADD COLUMN `apple_product_id` VARCHAR(128) NULL COMMENT 'Apple auto-renewable subscription product id' AFTER `auto_renew`;
