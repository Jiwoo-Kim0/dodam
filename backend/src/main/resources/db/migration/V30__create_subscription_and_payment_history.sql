-- S15P21D105-512/513/514: API 7 Subscription. ERD docs/ERD/DODAM_ERDCLOUD_IMPORT.sql의 `subscription`, `subscription_payment_history` 정의 그대로.
-- 번호 구간: V30~ BE-A (README "버전 번호 할당" 참고). V18~V29는 BE-B·Infra 예비 구간이다.
-- `subscription.card_id`가 있으면 같은 user_id의 card를 복합 외래키로 참조한다(README 무결성 규칙).
-- 시연 데이터는 database/seeds/DODAM_SUBSCRIPTION_SEED.sql이 적재한다(Flyway 밖).

CREATE TABLE `subscription` (
  `subscription_id` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `card_id` BIGINT NULL,
  `name` VARCHAR(150) NOT NULL,
  `payment_amount` BIGINT NOT NULL,
  `billing_cycle` VARCHAR(30) NOT NULL,
  `payment_day` INT NULL,
  `next_payment_date` DATE NULL,
  `end_date` DATE NULL,
  `status` VARCHAR(30) NOT NULL,
  `category` VARCHAR(50) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `canceled_at` DATETIME NULL,
  PRIMARY KEY (`subscription_id`),
  KEY `idx_subscription_user_id` (`user_id`),
  KEY `idx_subscription_user_card` (`user_id`, `card_id`),
  CONSTRAINT `chk_subscription_billing_cycle`
    CHECK (`billing_cycle` IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
  CONSTRAINT `chk_subscription_status`
    CHECK (`status` IN ('ACTIVE', 'PAUSED', 'CANCELED', 'EXPIRED')),
  CONSTRAINT `chk_subscription_payment_amount`
    CHECK (`payment_amount` > 0),
  CONSTRAINT `chk_subscription_payment_day`
    CHECK (`payment_day` IS NULL OR `payment_day` BETWEEN 1 AND 31),
  CONSTRAINT `chk_subscription_dates`
    CHECK (`end_date` IS NULL OR `next_payment_date` IS NULL OR `next_payment_date` <= `end_date`),
  CONSTRAINT `fk_subscription_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_subscription_card`
    FOREIGN KEY (`user_id`, `card_id`)
    REFERENCES `card` (`user_id`, `card_id`)
);

CREATE TABLE `subscription_payment_history` (
  `payment_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `subscription_id` VARCHAR(100) NOT NULL,
  `payment_date` DATE NOT NULL,
  `payment_amount` BIGINT NOT NULL,
  `result_code` VARCHAR(50) NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `result_message` VARCHAR(500) NULL,
  PRIMARY KEY (`payment_history_id`),
  KEY `idx_subscription_payment_history_subscription_id` (`subscription_id`),
  UNIQUE KEY `uk_subscription_payment_history_source`
    (`subscription_id`, `payment_date`, `payment_amount`),
  CONSTRAINT `chk_subscription_payment_history_amount`
    CHECK (`payment_amount` > 0),
  CONSTRAINT `chk_subscription_payment_history_retry_count`
    CHECK (`retry_count` >= 0),
  CONSTRAINT `fk_subscription_payment_history_subscription`
    FOREIGN KEY (`subscription_id`) REFERENCES `subscription` (`subscription_id`)
);
