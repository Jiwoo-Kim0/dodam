-- Jira: S15P21D105-530 (API 9 할부 조회·월별 일정·예측)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Installment 도메인이 조회하는 `installment` 한 테이블.
--
-- 선행 조건: `card`를 참조하므로 S15P21D105-529의 V11이 먼저 적용돼야 한다.
-- 즉 529 -> 530 순서로 병합해야 하며, 순서가 뒤집히면 이 마이그레이션이 실패한다.
-- V11이 `account`(BE-A, S15P21D105-501)를 기다리는 상태이므로 이 버전도 함께 대기한다.

CREATE TABLE `installment` (
  `installment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `card_id` BIGINT NOT NULL,
  `merchant_name` VARCHAR(255) NOT NULL,
  `total_amount` BIGINT NOT NULL,
  `months` INT NOT NULL,
  `remaining_principal` BIGINT NULL,
  `remaining_months` INT NOT NULL,
  `monthly_amount` BIGINT NOT NULL,
  `fee_rate` DECIMAL(10,4) NULL,
  `purchase_date` DATE NOT NULL,
  `first_payment_date` DATE NOT NULL,
  `next_payment_date` DATE NULL,
  `status` VARCHAR(30) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`installment_id`),
  KEY `idx_installment_card_id` (`card_id`),
  KEY `idx_installment_next_payment_date` (`next_payment_date`),
  CONSTRAINT `chk_installment_status`
    CHECK (`status` IN ('ACTIVE', 'COMPLETED', 'CANCELED', 'OVERDUE')),
  CONSTRAINT `chk_installment_total_amount`
    CHECK (`total_amount` > 0),
  CONSTRAINT `chk_installment_months`
    CHECK (`months` > 0),
  CONSTRAINT `chk_installment_remaining_principal`
    CHECK (`remaining_principal` IS NULL OR `remaining_principal` >= 0),
  CONSTRAINT `chk_installment_remaining_months`
    CHECK (`remaining_months` BETWEEN 0 AND `months`),
  CONSTRAINT `chk_installment_monthly_amount`
    CHECK (`monthly_amount` > 0),
  CONSTRAINT `chk_installment_fee_rate`
    CHECK (`fee_rate` IS NULL OR `fee_rate` >= 0),
  CONSTRAINT `chk_installment_payment_dates`
    CHECK (`purchase_date` <= `first_payment_date`),
  CONSTRAINT `fk_installment_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
);
