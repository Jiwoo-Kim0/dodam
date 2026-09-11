-- Jira: S15P21D105-538 (API 15 Cashflow 엔티티·Repository)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Cashflow 도메인이 소유하는 4테이블. 예측 실행·조회 API(541·542)와 이벤트 변환(539)은
-- 이 스키마 위에서 구현하며 여기서 테이블을 다시 만들지 않는다.
--
-- 중복 반영 차단
-- -------------
-- 명세 `15 Cashflow`의 6개 API가 공통으로 "예정 이벤트 중복 반영 금지"를 요구한다. 저장 단계의
-- 근거는 `uk_cashflow_event_forecast_dedupe`다. 같은 예측 안에서 동일 예정 이벤트를 두 번
-- 반영하면 예상잔액이 실제보다 낮아지므로 애플리케이션 검증이 아니라 유니크 제약으로 막는다.
--
-- 금액 부호
-- --------
-- `cashflow_event.amount`는 방향과 무관하게 항상 양수로 저장하고 `direction`으로 입·출금을
-- 구분한다(ERD 주요 코드값 표). 부호를 금액에 섞으면 합계·집계 쿼리에서 방향 조건이 빠져도
-- 결과가 그럴듯하게 나와 오류를 늦게 발견하게 된다.

CREATE TABLE `cashflow_forecast` (
  `forecast_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `base_date` DATE NOT NULL,
  `horizon_days` INT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `current_balance` BIGINT NULL,
  `available_today` BIGINT NULL,
  `minimum_balance` BIGINT NULL,
  `risk_date` DATE NULL,
  `calculation_version` VARCHAR(50) NULL,
  -- 동일 기준일 재요청을 캐시·멱등 처리하기 위한 요청 지문. 규칙은 S15P21D105-540에서 정한다.
  `request_hash` CHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`forecast_id`),
  KEY `idx_cashflow_forecast_user_base_date` (`user_id`, `base_date`, `created_at`),
  KEY `idx_cashflow_forecast_user_request_hash` (`user_id`, `request_hash`),
  CONSTRAINT `chk_cashflow_forecast_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `chk_cashflow_forecast_horizon_days`
    CHECK (`horizon_days` > 0),
  CONSTRAINT `fk_cashflow_forecast_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `cashflow_daily_balance` (
  `daily_balance_id` BIGINT NOT NULL AUTO_INCREMENT,
  `forecast_id` BIGINT NOT NULL,
  `balance_date` DATE NOT NULL,
  -- 마이너스통장 사용이나 잔액 부족 구간을 그대로 표현해야 하므로 음수를 허용한다.
  `expected_balance` BIGINT NOT NULL,
  PRIMARY KEY (`daily_balance_id`),
  UNIQUE KEY `uk_cashflow_daily_balance_forecast_date` (`forecast_id`, `balance_date`),
  CONSTRAINT `fk_cashflow_daily_balance_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);

CREATE TABLE `cashflow_event` (
  `event_id` BIGINT NOT NULL AUTO_INCREMENT,
  `forecast_id` BIGINT NOT NULL,
  `event_date` DATE NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `direction` VARCHAR(10) NOT NULL,
  `amount` BIGINT NOT NULL,
  `source_type` VARCHAR(30) NULL,
  `source_id` VARCHAR(100) NULL,
  `dedupe_key` CHAR(64) NOT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_cashflow_event_forecast_dedupe` (`forecast_id`, `dedupe_key`),
  KEY `idx_cashflow_event_forecast_date_type` (`forecast_id`, `event_date`, `type`),
  CONSTRAINT `chk_cashflow_event_direction`
    CHECK (`direction` IN ('INFLOW', 'OUTFLOW')),
  CONSTRAINT `chk_cashflow_event_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `fk_cashflow_event_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);

CREATE TABLE `cashflow_risk` (
  `risk_id` BIGINT NOT NULL AUTO_INCREMENT,
  `forecast_id` BIGINT NOT NULL,
  -- 특정 날짜에 묶이지 않는 위험(예: 기간 전체의 저축 여력 부족)이 있어 NULL을 허용한다.
  `risk_date` DATE NULL,
  `type` VARCHAR(50) NOT NULL,
  `severity` VARCHAR(30) NOT NULL,
  `message` VARCHAR(500) NULL,
  `expected_balance` BIGINT NULL,
  PRIMARY KEY (`risk_id`),
  KEY `idx_cashflow_risk_forecast_id` (`forecast_id`),
  CONSTRAINT `chk_cashflow_risk_severity`
    CHECK (`severity` IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT `fk_cashflow_risk_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);
