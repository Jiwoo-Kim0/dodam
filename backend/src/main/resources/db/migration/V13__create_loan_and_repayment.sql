-- Jira: S15P21D105-531 (API 10 대출 목록·요약·상세·상환내역·일정)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Loan 도메인이 조회하는 `loan`, `loan_repayment`.
--
-- 선행 조건: `financial_product`(529의 V10)와 `account`(BE-A, S15P21D105-501)를 참조한다.
-- ERD의 복합 외래키 정의를 그대로 유지하므로 account 마이그레이션 이후에 적용된다.
-- `loan`은 `card`에 의존하지 않으므로 529·530과의 병합 순서 제약은 V10에 한한다.
--
-- `loan_repayment`는 실제 상환 내역이며 향후 상환 일정을 저장하지 않는다.
-- 상환일정 API는 `repayment_type`, `repayment_day`, `balance`로 계산한다.

CREATE TABLE `loan` (
  `loan_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `withdrawal_account_type` VARCHAR(30) NOT NULL,
  `financial_product_id` BIGINT NULL,
  `institution_code` VARCHAR(30) NOT NULL,
  `institution_name` VARCHAR(150) NOT NULL,
  `account_no_encrypted` VARBINARY(512) NOT NULL,
  `account_no_hash` CHAR(64) NOT NULL,
  `account_no_masked` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(150) NOT NULL,
  `loan_type` VARCHAR(30) NOT NULL,
  `repayment_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `loan_period` INT NULL,
  `start_date` DATE NULL,
  `maturity_date` DATE NULL,
  `original_principal` BIGINT NOT NULL,
  `balance` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `repayment_day` INT NULL,
  `next_repayment_date` DATE NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`loan_id`),
  UNIQUE KEY `uk_loan_user_account_hash` (`user_id`, `account_no_hash`),
  KEY `idx_loan_financial_product_id` (`financial_product_id`),
  KEY `idx_loan_user_withdrawal_account`
    (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`),
  CONSTRAINT `chk_loan_type`
    CHECK (`loan_type` IN ('CREDIT', 'MORTGAGE', 'JEONSE', 'AUTO', 'OTHER')),
  CONSTRAINT `chk_loan_repayment_type`
    CHECK (`repayment_type` IN ('EQUAL_PRINCIPAL', 'EQUAL_PAYMENT', 'BULLET')),
  CONSTRAINT `chk_loan_status`
    CHECK (`status` IN ('ACTIVE', 'OVERDUE', 'COMPLETED', 'CLOSED')),
  CONSTRAINT `chk_loan_withdrawal_account_type`
    CHECK (`withdrawal_account_type` = 'DEMAND_DEPOSIT'),
  CONSTRAINT `chk_loan_period`
    CHECK (`loan_period` IS NULL OR `loan_period` > 0),
  CONSTRAINT `chk_loan_dates`
    CHECK (`start_date` IS NULL OR `maturity_date` IS NULL OR `start_date` <= `maturity_date`),
  CONSTRAINT `chk_loan_original_principal`
    CHECK (`original_principal` > 0),
  CONSTRAINT `chk_loan_balance`
    CHECK (`balance` >= 0 AND `balance` <= `original_principal`),
  CONSTRAINT `chk_loan_interest_rate`
    CHECK (`interest_rate` >= 0),
  CONSTRAINT `chk_loan_repayment_day`
    CHECK (`repayment_day` IS NULL OR `repayment_day` BETWEEN 1 AND 31),
  CONSTRAINT `fk_loan_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_loan_financial_product`
    FOREIGN KEY (`financial_product_id`)
    REFERENCES `financial_product` (`product_id`),
  CONSTRAINT `fk_loan_withdrawal_account`
    FOREIGN KEY (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

CREATE TABLE `loan_repayment` (
  `loan_repayment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `loan_id` BIGINT NOT NULL,
  `repayment_date` DATE NOT NULL,
  `amount` BIGINT NOT NULL,
  `principal` BIGINT NULL,
  `interest` BIGINT NULL,
  `status` VARCHAR(30) NOT NULL,
  `failure_reason` VARCHAR(500) NULL,
  PRIMARY KEY (`loan_repayment_id`),
  KEY `idx_loan_repayment_loan_id` (`loan_id`),
  UNIQUE KEY `uk_loan_repayment_source`
    (`loan_id`, `repayment_date`, `amount`),
  CONSTRAINT `chk_loan_repayment_status`
    CHECK (`status` IN ('SCHEDULED', 'PAID', 'FAILED', 'OVERDUE')),
  CONSTRAINT `chk_loan_repayment_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `chk_loan_repayment_principal`
    CHECK (`principal` IS NULL OR `principal` >= 0),
  CONSTRAINT `chk_loan_repayment_interest`
    CHECK (`interest` IS NULL OR `interest` >= 0),
  CONSTRAINT `fk_loan_repayment_loan`
    FOREIGN KEY (`loan_id`) REFERENCES `loan` (`loan_id`)
);
