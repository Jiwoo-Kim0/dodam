-- Jira: S15P21D105-501 (API 5 계좌 목록·상세·잔액) 소관 테이블
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 담당 안내
-- --------
-- `account`는 BE-A 소관이며 이 파일은 그 범위를 대신 구현한 것이 아니다. 스키마만 선반영한다.
-- 계좌 조회 API(S15P21D105-501)는 그대로 BE-A에서 진행하며, 그때 이 테이블을 재사용하고
-- 같은 테이블을 다시 만드는 마이그레이션을 추가하지 않는다.
--
-- 선반영 사유
-- ----------
-- S15P21D105-527이 병합되면서 `CardWithdrawalAccount`가 `account`를 매핑하는데 이 테이블을
-- 만드는 마이그레이션이 없어, 2026-09-09 기준 develop이 `Schema-validation: missing table
-- [account]`로 기동하지 못한다. 카드·대출의 출금계좌 복합 외래키도 이 테이블을 참조한다.
-- 담당자 부재 중 기동 불가를 방치하지 않기 위해 ERD 정의를 그대로 옮겨 먼저 추가한다.
--
-- 범위: `account` 한 테이블. `transaction`, `transaction_category` 등 나머지 BE-A 테이블은
-- 포함하지 않는다. 버전 번호는 BE-A 구간(V1~V9)에서 가져왔다.

CREATE TABLE `account` (
  `account_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `bank_code` VARCHAR(20) NOT NULL,
  `bank_name` VARCHAR(100) NOT NULL,
  `account_no_encrypted` VARBINARY(512) NOT NULL,
  `account_no_hash` CHAR(64) NOT NULL,
  `account_no_masked` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(100) NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  `balance` BIGINT NOT NULL,
  `last_transaction_date` DATE NULL,
  `currency` CHAR(3) NOT NULL DEFAULT 'KRW',
  `source_type` VARCHAR(20) NOT NULL DEFAULT 'SSAFY',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_account_user_account_hash` (`user_id`, `account_no_hash`),
  UNIQUE KEY `uk_account_user_account_id` (`user_id`, `account_id`),
  -- 카드·대출의 출금계좌 복합 외래키가 참조하는 유니크 키다.
  UNIQUE KEY `uk_account_user_id_type` (`user_id`, `account_id`, `account_type`),
  KEY `idx_account_user_bank` (`user_id`, `bank_code`),
  KEY `idx_account_user_type` (`user_id`, `account_type`),
  KEY `idx_account_user_inactive` (`user_id`, `last_transaction_date`, `balance`),
  CONSTRAINT `chk_account_type`
    CHECK (`account_type` IN ('DEMAND_DEPOSIT', 'MINUS', 'DEPOSIT', 'SAVINGS')),
  CONSTRAINT `chk_account_status`
    CHECK (`status` IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
  CONSTRAINT `chk_account_balance_by_type`
    CHECK ((`account_type` = 'MINUS' AND `balance` <= 0)
        OR (`account_type` <> 'MINUS' AND `balance` >= 0)),
  CONSTRAINT `chk_account_source_type`
    CHECK (`source_type` IN ('SSAFY', 'MANUAL', 'SEED')),
  CONSTRAINT `fk_account_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
