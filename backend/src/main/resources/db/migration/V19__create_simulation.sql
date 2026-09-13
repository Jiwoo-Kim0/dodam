-- Jira: S15P21D105-550 (API 17 Simulation 공통 모델)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07) 1089~1150행
--
-- 범위: Simulation 도메인이 소유하는 3테이블. 유형별 시뮬레이션 API(551 spending·saving 이후)는 이 스키마 위에서
-- 구현하며 테이블을 다시 만들지 않는다. `recommendation`(V18, S15P21D105-547)과는 FK 관계가 없다.
--
-- 번호: V18~V29 BE-B 예비 구간의 두 번째 번호. V18은 547이 선점했고 V30(BE-A)이 먼저 적용된 DB에는
-- out-of-order로 들어간다(migration/README.md).
--
-- 스냅샷 컬럼
-- ----------
-- `before_snapshot`·`after_snapshot`은 실행 시점 기준선과 계산 결과를 원본 그대로 남긴다. 조회 API는 이 값을
-- 재계산 없이 돌려주므로 NOT NULL이다. `request_snapshot`에는 요청 파라미터와 요청 지문(requestHash)을 두어
-- 같은 날 같은 요청을 다시 만들지 않는 근거로 쓴다. 계좌번호 원문은 어느 스냅샷에도 넣지 않는다.
--
-- 상태
-- ----
-- CHECK는 ERD의 4종을 그대로 두지만 현재 실행은 동기이므로 저장되는 행은 항상 'COMPLETED'다.
-- 삭제는 `deleted_at`으로만 표시한다(시뮬레이션 이력은 개선 효과 비교의 근거라 하드 삭제하지 않는다).

CREATE TABLE `simulation` (
  `simulation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NULL,
  `summary` VARCHAR(500) NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
  `request_snapshot` JSON NULL,
  `before_snapshot` JSON NOT NULL,
  `after_snapshot` JSON NOT NULL,
  `comparison` JSON NULL,
  `cashflow_impact` JSON NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`simulation_id`),
  UNIQUE KEY `uk_simulation_user_simulation_id` (`user_id`, `simulation_id`),
  KEY `idx_simulation_user_type_created` (`user_id`, `type`, `created_at`),
  CONSTRAINT `chk_simulation_type`
    CHECK (`type` IN ('SPENDING', 'SAVING', 'SAVING_PRODUCT', 'LOAN_PREPAYMENT',
                      'LOAN_REFINANCE', 'LOAN_NEW', 'INSTALLMENT', 'REVOLVING',
                      'CASH_ADVANCE', 'COMBINED')),
  CONSTRAINT `chk_simulation_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `fk_simulation_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `simulation_scenario` (
  `scenario_id` BIGINT NOT NULL AUTO_INCREMENT,
  `simulation_id` BIGINT NOT NULL,
  `sequence_no` INT NOT NULL,
  `scenario_type` VARCHAR(50) NOT NULL,
  `parameters` JSON NOT NULL,
  PRIMARY KEY (`scenario_id`),
  UNIQUE KEY `uk_simulation_scenario_sequence` (`simulation_id`, `sequence_no`),
  CONSTRAINT `chk_simulation_scenario_sequence_no`
    CHECK (`sequence_no` > 0),
  CONSTRAINT `fk_simulation_scenario_simulation`
    FOREIGN KEY (`simulation_id`) REFERENCES `simulation` (`simulation_id`)
);

CREATE TABLE `simulation_goal_impact` (
  `simulation_goal_impact_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `simulation_id` BIGINT NOT NULL,
  `goal_id` BIGINT NOT NULL,
  `before_forecast_date` DATE NULL,
  `after_forecast_date` DATE NULL,
  `difference_days` INT NULL,
  PRIMARY KEY (`simulation_goal_impact_id`),
  UNIQUE KEY `uk_simulation_goal_impact_simulation_goal`
    (`simulation_id`, `goal_id`),
  KEY `idx_simulation_goal_impact_user_simulation` (`user_id`, `simulation_id`),
  KEY `idx_simulation_goal_impact_user_goal` (`user_id`, `goal_id`),
  CONSTRAINT `fk_simulation_goal_impact_simulation`
    FOREIGN KEY (`user_id`, `simulation_id`)
    REFERENCES `simulation` (`user_id`, `simulation_id`),
  CONSTRAINT `fk_simulation_goal_impact_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`)
);
