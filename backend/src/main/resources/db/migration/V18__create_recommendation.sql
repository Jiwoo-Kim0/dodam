-- Jira: S15P21D105-547 (API 16 Recommendation 저장 기반)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Recommendation 도메인이 소유하는 3테이블. 생성·조회 API(548)와 상태 변경·이력 API(549)는
-- 이 스키마 위에서 구현하며 테이블을 다시 만들지 않는다. `improvement_action`은 별도 이슈 소관이라
-- 포함하지 않는다. `uk_recommendation_user_recommendation_id`는 그 테이블의 복합 FK 대상이므로
-- 여기서 미리 만든다.
--
-- 번호: V18~V29는 BE-B·Infra 예비 구간이다(README). 2026-09-13 기준 원격 브랜치 어디에도 V18이
-- 없음을 확인하고 잡았다. V30이 먼저 적용된 DB에는 out-of-order로 들어간다.
--
-- 중복 생성 방지
-- -------------
-- `generation_key`는 같은 진단·목표·생성기·후보에서 만들어진 추천을 한 번만 저장하기 위한 키다.
-- ERD상 NULL 허용이지만 서비스 생성 경로는 항상 채운다(MySQL은 NULL끼리 UNIQUE 충돌이 없어
-- NULL 행은 중복 방지가 되지 않는다). 키 구성은 RecommendationGenerationKey 참고.
--
-- `recommendation_reason.code`도 같은 이유로 서비스 경로에서 필수다. NULL이면
-- `uk_recommendation_reason_code`가 같은 추천의 중복 근거를 막지 못한다.
--
-- 근거 보존
-- --------
-- 추천·근거·이력은 계산 근거를 남기기 위해 하드 삭제하지 않는다(ERD 설명). 재생성은 새 행을 만들지
-- 않고 기존 행을 돌려주며, 상태 변경은 `recommendation_history`에 행을 추가한다.

CREATE TABLE `recommendation` (
  `recommendation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `diagnosis_id` BIGINT NOT NULL,
  `goal_id` BIGINT NULL,
  `domain` VARCHAR(50) NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  `priority` INT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `monthly_cost_saving` BIGINT NULL,
  `annual_cost_saving` BIGINT NULL,
  `interest_saving` BIGINT NULL,
  `risk_reduction` VARCHAR(100) NULL,
  `current_forecast_date` DATE NULL,
  `improved_forecast_date` DATE NULL,
  `advanced_days` INT NULL,
  `contribution_amount` BIGINT NULL,
  `generation_key` CHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`recommendation_id`),
  UNIQUE KEY `uk_recommendation_user_recommendation_id` (`user_id`, `recommendation_id`),
  UNIQUE KEY `uk_recommendation_user_generation_key` (`user_id`, `generation_key`),
  KEY `idx_recommendation_user_diagnosis` (`user_id`, `diagnosis_id`),
  KEY `idx_recommendation_user_goal` (`user_id`, `goal_id`),
  KEY `idx_recommendation_user_status_domain` (`user_id`, `status`, `domain`),
  CONSTRAINT `chk_recommendation_status`
    CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'DEFERRED', 'COMPLETED')),
  CONSTRAINT `chk_recommendation_priority`
    CHECK (`priority` > 0),
  CONSTRAINT `chk_recommendation_saving_amounts`
    CHECK ((`monthly_cost_saving` IS NULL OR `monthly_cost_saving` >= 0)
       AND (`annual_cost_saving` IS NULL OR `annual_cost_saving` >= 0)
       AND (`interest_saving` IS NULL OR `interest_saving` >= 0)
       AND (`contribution_amount` IS NULL OR `contribution_amount` >= 0)),
  CONSTRAINT `fk_recommendation_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_recommendation_diagnosis`
    FOREIGN KEY (`user_id`, `diagnosis_id`)
    REFERENCES `diagnosis` (`user_id`, `diagnosis_id`),
  CONSTRAINT `fk_recommendation_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`)
);

CREATE TABLE `recommendation_reason` (
  `reason_id` BIGINT NOT NULL AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `code` VARCHAR(50) NULL,
  `description` TEXT NOT NULL,
  `evidence` JSON NULL,
  PRIMARY KEY (`reason_id`),
  KEY `idx_recommendation_reason_recommendation_id` (`recommendation_id`),
  UNIQUE KEY `uk_recommendation_reason_code` (`recommendation_id`, `code`),
  CONSTRAINT `fk_recommendation_reason_recommendation`
    FOREIGN KEY (`recommendation_id`) REFERENCES `recommendation` (`recommendation_id`)
);

CREATE TABLE `recommendation_history` (
  `recommendation_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `reason` VARCHAR(500) NULL,
  `memo` VARCHAR(500) NULL,
  `changed_at` DATETIME NOT NULL,
  PRIMARY KEY (`recommendation_history_id`),
  KEY `idx_recommendation_history_recommendation_changed`
    (`recommendation_id`, `changed_at`),
  CONSTRAINT `chk_recommendation_history_status`
    CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'DEFERRED', 'COMPLETED')),
  CONSTRAINT `fk_recommendation_history_recommendation`
    FOREIGN KEY (`recommendation_id`) REFERENCES `recommendation` (`recommendation_id`)
);
