-- Dealer settlement should be tracked per (order, dealer), not only per order.
CREATE TABLE IF NOT EXISTS `payment_order_dealer_settlement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    `order_id` VARCHAR(64) NOT NULL COMMENT 'Business order id',
    `dealer_id` BIGINT NOT NULL COMMENT 'Dealer id',
    `settled_by` BIGINT DEFAULT NULL COMMENT 'Operator user id',
    `settled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Settlement time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    UNIQUE KEY `uk_order_dealer_settlement` (`order_id`, `dealer_id`),
    KEY `idx_order_dealer_settlement_order` (`order_id`),
    KEY `idx_order_dealer_settlement_dealer` (`dealer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per dealer settlement records per order';

-- Backfill direct-dealer settled records from legacy flags.
INSERT INTO `payment_order_dealer_settlement` (`order_id`, `dealer_id`, `settled_by`, `settled_at`, `created_at`, `updated_at`)
SELECT
    `order_id`,
    `dealer_id`,
    NULL,
    COALESCE(`updated_at`, `paid_at`, NOW()),
    NOW(),
    NOW()
FROM `payment_order`
WHERE `order_id` IS NOT NULL
  AND `dealer_id` IS NOT NULL
  AND COALESCE(`dealer_is_settled`, `is_settled`, 0) = 1
ON DUPLICATE KEY UPDATE
    `settled_at` = VALUES(`settled_at`),
    `updated_at` = NOW();
