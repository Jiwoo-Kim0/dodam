-- Jira: S15P21D105-504 (API 6 거래 조회·메모·카테고리·숨김)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Transaction 도메인이 소유하는 `transaction_category`, `transaction`.
-- 두 테이블은 카드(8)·마이너스통장(10-1)·상품 추천(18)이 이미 JDBC로 읽고 있으나 마이그레이션이
-- 없어 MySQL에서는 조회가 실패하던 상태다. 이 버전이 BE-A 구간(V1~V9)에서 그 공백을 메운다.
--
-- 선행 조건: `transaction`은 ERD대로 `account`(V3), `card`(V11)를 복합 외래키로 참조한다.
-- 기존 DB에는 V11까지 적용된 뒤 V5가 들어오므로 out-of-order 적용을 전제로 한다(README 참고).
--
-- `transaction_category`의 기준 행은 코드값 성격의 참조 데이터라 시연용 seed와 달리 여기서 함께 넣는다.
-- 코드는 애플리케이션이 카테고리 필터와 소비유형 분류에 그대로 쓰므로 변경 시 ERD와 함께 조정한다.

CREATE TABLE `transaction_category` (
  `category_id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `default_spending_type` VARCHAR(20) NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_transaction_category_code` (`code`),
  CONSTRAINT `chk_transaction_category_spending_type`
    CHECK (`default_spending_type` IS NULL OR `default_spending_type` IN ('FIXED', 'VARIABLE', 'NON_SPENDING')),
  CONSTRAINT `chk_transaction_category_active`
    CHECK (`active` IN (0, 1))
);

CREATE TABLE `transaction` (
  `transaction_id` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `account_id` BIGINT NULL,
  `card_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `transaction_date` DATE NOT NULL,
  `transaction_time` TIME NULL,
  `transaction_type` VARCHAR(30) NOT NULL,
  `amount` BIGINT NOT NULL,
  `balance_after` BIGINT NULL,
  `merchant_name` VARCHAR(255) NULL,
  `payment_method` VARCHAR(30) NULL,
  `spending_type` VARCHAR(20) NULL,
  `summary` VARCHAR(255) NULL,
  `memo` VARCHAR(500) NULL,
  `hidden` TINYINT(1) NOT NULL DEFAULT 0,
  `source_type` VARCHAR(20) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`transaction_id`),
  KEY `idx_transaction_user_id` (`user_id`),
  KEY `idx_transaction_user_account` (`user_id`, `account_id`),
  KEY `idx_transaction_user_card` (`user_id`, `card_id`),
  KEY `idx_transaction_category_id` (`category_id`),
  KEY `idx_transaction_user_date` (`user_id`, `transaction_date`),
  CONSTRAINT `chk_transaction_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `chk_transaction_type`
    CHECK (`transaction_type` IN ('INCOME', 'EXPENSE', 'TRANSFER')),
  CONSTRAINT `chk_transaction_source_type`
    CHECK (`source_type` IN ('SSAFY', 'MANUAL', 'SEED')),
  CONSTRAINT `chk_transaction_spending_type`
    CHECK (`spending_type` IS NULL OR `spending_type` IN ('FIXED', 'VARIABLE', 'NON_SPENDING')),
  CONSTRAINT `fk_transaction_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_transaction_account`
    FOREIGN KEY (`user_id`, `account_id`)
    REFERENCES `account` (`user_id`, `account_id`),
  CONSTRAINT `fk_transaction_card`
    FOREIGN KEY (`user_id`, `card_id`)
    REFERENCES `card` (`user_id`, `card_id`),
  CONSTRAINT `fk_transaction_category`
    FOREIGN KEY (`category_id`) REFERENCES `transaction_category` (`category_id`)
);

-- 카테고리 기준 행. default_spending_type은 카테고리 변경 시 transaction.spending_type의 기본값으로 쓰인다.
INSERT INTO `transaction_category` (`code`, `name`, `default_spending_type`, `active`) VALUES
  ('FOOD', '식비', 'VARIABLE', 1),
  ('CAFE', '카페', 'VARIABLE', 1),
  ('GROCERY', '마트·편의점', 'VARIABLE', 1),
  ('PUBLIC_TRANSPORT', '대중교통', 'VARIABLE', 1),
  ('TAXI', '택시', 'VARIABLE', 1),
  ('SHOPPING', '쇼핑', 'VARIABLE', 1),
  ('BEAUTY', '뷰티·미용', 'VARIABLE', 1),
  ('CULTURE', '문화·여가', 'VARIABLE', 1),
  ('TRAVEL', '여행', 'VARIABLE', 1),
  ('MEDICAL', '의료·건강', 'VARIABLE', 1),
  ('EDUCATION', '교육', 'VARIABLE', 1),
  ('HOUSING', '주거·관리비', 'FIXED', 1),
  ('UTILITIES', '통신·공과금', 'FIXED', 1),
  ('INSURANCE', '보험', 'FIXED', 1),
  ('SUBSCRIPTION', '구독', 'FIXED', 1),
  ('LOAN_REPAYMENT', '대출상환', 'FIXED', 1),
  ('SAVING', '저축·투자', 'NON_SPENDING', 1),
  ('TRANSFER', '이체', 'NON_SPENDING', 1),
  ('INCOME', '수입', 'NON_SPENDING', 1),
  ('ETC', '기타', 'VARIABLE', 1);
