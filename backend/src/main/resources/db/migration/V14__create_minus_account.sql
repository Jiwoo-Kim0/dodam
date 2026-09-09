-- Jira: S15P21D105-534 (API 10-1 마이너스통장 목록·상세·이자·거래·알림·상환·이자납부)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: MinusAccount 도메인이 소유하는 `minus_account` 한 테이블.
--
-- 선행 조건: `account`(BE-A, S15P21D105-501의 V3)의 유니크 키
-- `uk_account_user_id_type (user_id, account_id, account_type)`을 복합 외래키로 참조한다.
-- 마이너스통장은 먼저 `account`에 `account_type = 'MINUS'`로 만든 뒤 같은 `account_id`로
-- 이 테이블의 상세행을 만든다. `account_id`는 AUTO_INCREMENT가 아니라 상위 행의 값을 그대로 받는다.
--
-- `transaction`과 `notification`은 각각 BE-A와 S15P21D105-458 소관이므로 여기서 만들지 않는다.
-- 거래내역·알림 API는 그 테이블을 읽기만 한다.
--
-- 금액 정합 제약
-- -------------
-- `available_amount`는 파생 저장값이며 `chk_minus_account_available_amount`가 원천값과의
-- 일치를 강제한다. 상환·이자납부는 `used_amount`, `available_amount`와 상위
-- `account.balance`(= -used_amount)를 한 트랜잭션에서 함께 갱신해야 한다.

CREATE TABLE `minus_account` (
  `account_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `limit_amount` BIGINT NOT NULL,
  `used_amount` BIGINT NOT NULL,
  `available_amount` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `accrued_interest` BIGINT NOT NULL,
  `next_interest_date` DATE NULL,
  `maturity_date` DATE NULL,
  `overdue_days` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_minus_account_user_account` (`user_id`, `account_id`),
  KEY `idx_minus_account_parent` (`user_id`, `account_id`, `account_type`),
  CONSTRAINT `chk_minus_account_type`
    CHECK (`account_type` = 'MINUS'),
  CONSTRAINT `chk_minus_account_limit_amount`
    CHECK (`limit_amount` > 0),
  CONSTRAINT `chk_minus_account_used_amount`
    CHECK (`used_amount` >= 0 AND `used_amount` <= `limit_amount`),
  CONSTRAINT `chk_minus_account_available_amount`
    CHECK (`available_amount` = `limit_amount` - `used_amount`),
  CONSTRAINT `chk_minus_account_interest_rate`
    CHECK (`interest_rate` >= 0),
  CONSTRAINT `chk_minus_account_accrued_interest`
    CHECK (`accrued_interest` >= 0),
  CONSTRAINT `chk_minus_account_overdue_days`
    CHECK (`overdue_days` >= 0),
  CONSTRAINT `fk_minus_account_account`
    FOREIGN KEY (`user_id`, `account_id`, `account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);
