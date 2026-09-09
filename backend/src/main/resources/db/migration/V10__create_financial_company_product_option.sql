-- Jira: S15P21D105-525 (API 18 금융회사·상품 조회), S15P21D105-526 (상품 비교·카드 추천)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Product 도메인이 매핑하는 `financial_company`, `financial_product`,
-- `financial_product_option` 세 테이블.
-- 525·526은 이미 develop에 병합됐으나 마이그레이션이 없어 prod(ddl-auto: validate)에서
-- 기동이 불가능한 상태였다. 뒤의 Card·Loan이 `financial_product`를 참조하므로 먼저 생성한다.
--
-- 버전 번호: V3~V9는 BE-A(계좌·거래 등) 몫으로 비워 둔다. README의 버전 할당 규칙 참고.

CREATE TABLE `financial_company` (
  `company_id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_code` VARCHAR(30) NOT NULL,
  `company_name` VARCHAR(150) NOT NULL,
  `sector` VARCHAR(50) NULL,
  PRIMARY KEY (`company_id`),
  UNIQUE KEY `uk_financial_company_code` (`company_code`)
);

CREATE TABLE `financial_product` (
  `product_id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `source_type` VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
  `external_product_code` VARCHAR(100) NULL,
  `product_type` VARCHAR(30) NOT NULL,
  `product_name` VARCHAR(255) NOT NULL,
  `join_way` VARCHAR(255) NULL,
  `saving_type` VARCHAR(30) NULL,
  `loan_type` VARCHAR(30) NULL,
  `special_condition` TEXT NULL,
  `join_member` TEXT NULL,
  `min_rate` DECIMAL(10,4) NULL,
  `max_rate` DECIMAL(10,4) NULL,
  `max_limit` BIGINT NULL,
  `baseline_performance` BIGINT NULL,
  `max_benefit_limit` BIGINT NULL,
  `description` TEXT NULL,
  `benefits` JSON NULL,
  `synced_at` DATETIME NOT NULL,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_financial_product_natural`
    (`company_id`, `product_type`, `product_name`),
  UNIQUE KEY `uk_financial_product_external`
    (`source_type`, `company_id`, `external_product_code`),
  KEY `idx_financial_product_company_id` (`company_id`),
  KEY `idx_financial_product_type_rate` (`product_type`, `min_rate`, `max_rate`),
  CONSTRAINT `chk_financial_product_type`
    CHECK (`product_type` IN ('DEPOSIT', 'SAVINGS', 'LOAN', 'CARD')),
  CONSTRAINT `chk_financial_product_source_type`
    CHECK (`source_type` IN ('FSS', 'ISSUER', 'MANUAL', 'SEED')),
  CONSTRAINT `chk_financial_product_external_code`
    CHECK ((`source_type` IN ('FSS', 'ISSUER') AND `external_product_code` IS NOT NULL)
        OR (`source_type` IN ('MANUAL', 'SEED'))),
  CONSTRAINT `chk_financial_product_rates`
    CHECK ((`min_rate` IS NULL OR `min_rate` >= 0)
       AND (`max_rate` IS NULL OR `max_rate` >= 0)
       AND (`min_rate` IS NULL OR `max_rate` IS NULL OR `min_rate` <= `max_rate`)),
  CONSTRAINT `chk_financial_product_limits`
    CHECK ((`max_limit` IS NULL OR `max_limit` >= 0)
       AND (`baseline_performance` IS NULL OR `baseline_performance` >= 0)
       AND (`max_benefit_limit` IS NULL OR `max_benefit_limit` >= 0)),
  CONSTRAINT `fk_financial_product_company`
    FOREIGN KEY (`company_id`) REFERENCES `financial_company` (`company_id`)
);

CREATE TABLE `financial_product_option` (
  `product_option_id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL,
  `term` INT NULL,
  `rate` DECIMAL(10,4) NULL,
  `preferred_rate` DECIMAL(10,4) NULL,
  `rate_type` VARCHAR(30) NULL,
  `min_rate` DECIMAL(10,4) NULL,
  `max_rate` DECIMAL(10,4) NULL,
  `repayment_type` VARCHAR(30) NULL,
  `limit_amount` BIGINT NULL,
  PRIMARY KEY (`product_option_id`),
  KEY `idx_financial_product_option_product_id` (`product_id`),
  CONSTRAINT `chk_financial_product_option_term`
    CHECK (`term` IS NULL OR `term` > 0),
  CONSTRAINT `chk_financial_product_option_rates`
    CHECK ((`rate` IS NULL OR `rate` >= 0)
       AND (`preferred_rate` IS NULL OR `preferred_rate` >= 0)
       AND (`min_rate` IS NULL OR `min_rate` >= 0)
       AND (`max_rate` IS NULL OR `max_rate` >= 0)
       AND (`min_rate` IS NULL OR `max_rate` IS NULL OR `min_rate` <= `max_rate`)),
  CONSTRAINT `chk_financial_product_option_limit_amount`
    CHECK (`limit_amount` IS NULL OR `limit_amount` >= 0),
  CONSTRAINT `fk_financial_product_option_product`
    FOREIGN KEY (`product_id`) REFERENCES `financial_product` (`product_id`)
);
