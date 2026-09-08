-- ============================================================================
-- [초안] 금융상품 관련 테이블 생성
--
-- 작성 경위 (인프라):
--   상품 API MR(S15P21D105-525, -526)이 엔티티만 병합하고 이 테이블들을 만드는
--   마이그레이션을 포함하지 않아, Hibernate ddl-auto=validate가 기동 시점에 실패하며
--   운영 백엔드가 재시작 루프에 빠졌다(외부 502). 임시로 ddl-auto를 none으로 낮춰 복구한 상태다.
--
--   이 파일은 docs/ERD/DODAM_ERDCLOUD_IMPORT.sql의 해당 테이블 DDL을 그대로 옮긴 초안이다.
--   백엔드 팀에서 엔티티 매핑과 대조해 검토한 뒤
--   backend/src/main/resources/db/migration/ 로 옮겨 사용할 것.
--
--   이 마이그레이션이 반영되면 infra/docker-compose.yml의
--   SPRING_JPA_HIBERNATE_DDL_AUTO=none 임시 조치를 제거하고 validate로 되돌려야 한다.
--
--   버전 번호(V3)는 병합 시점의 기존 마이그레이션 순서에 맞춰 조정할 것.
-- ============================================================================

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
