-- Jira: S15P21D105-590 (API 19 Improvement 저장 기반)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql `improvement_action` (2026-09-16 개정본)
--
-- 범위: 개선 행동 1건. 추천을 ACCEPT하면 생성되고 추천 상태를 따라 종료된다. API 19에는 쓰기 엔드포인트가
-- 없으므로(명세 11개가 모두 조회 + 재진단) 행동의 생명주기는 추천 상태 변경(549)이 유일한 입구다.
--
-- 추천과 중복 저장하지 않는다
-- -------------------------
-- `goal_id`, `domain`, `type`, 예상 효과(monthly_cost_saving 등)는 `recommendation`에 이미 있다. 여기 다시 두면
-- 두 행이 어긋날 수 있으므로 저장하지 않고 조회에서 조인해 읽는다. 이 테이블의 금액 컬럼은 전부 **실측값**이며
-- 예상치가 아니다(응답의 expected* / actual* 구분이 그대로 여기서 나온다).
-- `title`만 예외로 복사한다 — 수락 당시 사용자가 본 문구를 보존하기 위해서다(추천 문구는 이후 바뀔 수 있다).
--
-- ERD 대비 추가 컬럼
-- ----------------
-- `user_id`      : 소유권 조회를 추천 조인 없이 하고, 복합 FK로 추천과 같은 사용자임을 DB가 보장하게 한다.
-- `planned_at`   : 수락 시각. ERD의 started_at은 "실제 실행 시작"이라 수락 시각과 의미가 다르다.
-- `canceled_at`  : CANCELED 상태의 시각. status만 있고 시각이 없으면 이력을 복원할 수 없다.
-- `created_at`/`updated_at` : 다른 도메인 테이블과 같은 감사 컬럼.
-- 위 5개는 docs/ERD/DODAM_ERDCLOUD_IMPORT.sql에도 함께 반영했다.

CREATE TABLE `improvement_action` (
  `action_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `recommendation_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `before_snapshot` JSON NULL,
  `after_snapshot` JSON NULL,
  `spending_reduction` BIGINT NULL,
  `saving_increase` BIGINT NULL,
  `debt_reduction` BIGINT NULL,
  `monthly_cost_saving` BIGINT NULL,
  `annual_cost_saving` BIGINT NULL,
  `interest_saving` BIGINT NULL,
  `goal_advanced_days` INT NULL,
  `planned_at` DATETIME NOT NULL,
  `started_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  `canceled_at` DATETIME NULL,
  `measured_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`action_id`),
  -- 추천 1건당 행동은 최대 1건이다(ERD 1:0..1). 재수락(DEFERRED → ACCEPTED)이 두 번째 행을 만들지 못하게 막는다.
  UNIQUE KEY `uk_improvement_action_recommendation_id` (`recommendation_id`),
  KEY `idx_improvement_action_user_status` (`user_id`, `status`),
  KEY `idx_improvement_action_user_planned` (`user_id`, `planned_at`),
  KEY `idx_improvement_action_user_measured` (`user_id`, `measured_at`),
  CONSTRAINT `chk_improvement_action_status`
    CHECK (`status` IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED')),
  CONSTRAINT `chk_improvement_action_amounts`
    CHECK ((`spending_reduction` IS NULL OR `spending_reduction` >= 0)
       AND (`saving_increase` IS NULL OR `saving_increase` >= 0)
       AND (`debt_reduction` IS NULL OR `debt_reduction` >= 0)
       AND (`monthly_cost_saving` IS NULL OR `monthly_cost_saving` >= 0)
       AND (`annual_cost_saving` IS NULL OR `annual_cost_saving` >= 0)
       AND (`interest_saving` IS NULL OR `interest_saving` >= 0)),
  CONSTRAINT `chk_improvement_action_dates`
    CHECK (`started_at` IS NULL OR `completed_at` IS NULL OR `started_at` <= `completed_at`),
  CONSTRAINT `fk_improvement_action_recommendation`
    FOREIGN KEY (`user_id`, `recommendation_id`)
    REFERENCES `recommendation` (`user_id`, `recommendation_id`)
);
