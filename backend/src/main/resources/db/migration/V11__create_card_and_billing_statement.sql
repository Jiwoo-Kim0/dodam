-- Jira: S15P21D105-527 (API 8 카드 목록·상세·사용내역), S15P21D105-529 (카드 집계·혜택·결제설정)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Card 도메인이 조회하는 `card`, `card_billing_statement`.
-- `card_benefit`은 527·529 구현이 참조하지 않아 이번 범위에서 제외한다. 혜택 조회는
-- `financial_product.benefits`를 사용한다. 해당 테이블이 필요한 시점의 티켓에서 추가한다.
--
-- 선행 조건: `card`가 ERD대로 `account`를 복합 외래키로 참조한다.
-- `account` 테이블은 BE-A(S15P21D105-501 계좌 목록·상세·잔액) 소관이므로 이 마이그레이션은
-- 해당 버전이 들어오기 전에는 적용되지 않는다. FK를 빼고 나중에 추가하면 데이터 적재 후
-- 제약 추가가 어려워지므로 ERD 정의를 그대로 유지한다.

CREATE TABLE `card` (
  `card_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `withdrawal_account_type` VARCHAR(30) NOT NULL,
  `financial_product_id` BIGINT NULL,
  `card_no_encrypted` VARBINARY(512) NOT NULL,
  `card_no_hash` CHAR(64) NOT NULL,
  `card_no_masked` VARCHAR(100) NOT NULL,
  `issuer_code` VARCHAR(30) NOT NULL,
  `issuer_name` VARCHAR(100) NOT NULL,
  `card_name` VARCHAR(150) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `credit_limit` BIGINT NOT NULL,
  `baseline_performance` BIGINT NULL,
  `max_benefit_limit` BIGINT NULL,
  `annual_fee` BIGINT NULL,
  `expiry_month` CHAR(7) NULL,
  `withdrawal_day` INT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`card_id`),
  UNIQUE KEY `uk_card_user_card_hash` (`user_id`, `card_no_hash`),
  UNIQUE KEY `uk_card_user_card_id` (`user_id`, `card_id`),
  KEY `idx_card_financial_product_id` (`financial_product_id`),
  KEY `idx_card_user_issuer` (`user_id`, `issuer_code`),
  KEY `idx_card_user_withdrawal_account`
    (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`),
  CONSTRAINT `chk_card_status`
    CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELED')),
  CONSTRAINT `chk_card_withdrawal_account_type`
    CHECK (`withdrawal_account_type` = 'DEMAND_DEPOSIT'),
  CONSTRAINT `chk_card_credit_limit`
    CHECK (`credit_limit` >= 0),
  CONSTRAINT `chk_card_baseline_performance`
    CHECK (`baseline_performance` IS NULL OR `baseline_performance` >= 0),
  CONSTRAINT `chk_card_max_benefit_limit`
    CHECK (`max_benefit_limit` IS NULL OR `max_benefit_limit` >= 0),
  CONSTRAINT `chk_card_annual_fee`
    CHECK (`annual_fee` IS NULL OR `annual_fee` >= 0),
  CONSTRAINT `chk_card_withdrawal_day`
    CHECK (`withdrawal_day` IS NULL OR `withdrawal_day` BETWEEN 1 AND 31),
  CONSTRAINT `fk_card_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_card_financial_product`
    FOREIGN KEY (`financial_product_id`)
    REFERENCES `financial_product` (`product_id`),
  CONSTRAINT `fk_card_withdrawal_account`
    FOREIGN KEY (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

CREATE TABLE `card_billing_statement` (
  `card_id` BIGINT NOT NULL,
  `billing_month` CHAR(7) NOT NULL,
  `billing_date` DATE NOT NULL,
  `total_balance` BIGINT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `payment_date` DATE NULL,
  PRIMARY KEY (`card_id`, `billing_month`),
  CONSTRAINT `chk_card_billing_statement_status`
    CHECK (`status` IN ('SCHEDULED', 'PAID', 'OVERDUE', 'FAILED')),
  CONSTRAINT `chk_card_billing_statement_total_balance`
    CHECK (`total_balance` >= 0),
  CONSTRAINT `fk_card_billing_statement_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
);
