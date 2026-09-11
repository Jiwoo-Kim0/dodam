-- Jira: S15P21D105-507 (API 6 목표·이상소비·결제수단 분석)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: `goal`(V5, 543이 선반영)의 하위행 `saving_goal`, `spending_goal`.
-- 소비목표 비교(`GET /api/v1/spending/goal-comparison`)가 `spending_goal.monthly_budget`을 읽어야 해서
-- Goal API(S15P21D105-508~511)보다 먼저 만든다. 둘 다 BE-A 소관이므로 같은 테이블을 다시 만들지 않는다.
-- `goal_account`, `goal_history`는 Goal API에서 추가한다.
--
-- 선행 조건: `goal`의 `uk_goal_id_type (goal_id, type)`을 복합 외래키로 참조한다(V5).

CREATE TABLE `saving_goal` (
  `goal_id` BIGINT NOT NULL,
  `goal_type` VARCHAR(20) NOT NULL,
  `target_amount` BIGINT NOT NULL,
  `target_date` DATE NOT NULL,
  PRIMARY KEY (`goal_id`),
  KEY `idx_saving_goal_parent` (`goal_id`, `goal_type`),
  CONSTRAINT `chk_saving_goal_type`
    CHECK (`goal_type` = 'SAVING'),
  CONSTRAINT `chk_saving_goal_target_amount`
    CHECK (`target_amount` > 0),
  CONSTRAINT `fk_saving_goal_goal`
    FOREIGN KEY (`goal_id`, `goal_type`)
    REFERENCES `goal` (`goal_id`, `type`)
);

CREATE TABLE `spending_goal` (
  `goal_id` BIGINT NOT NULL,
  `goal_type` VARCHAR(20) NOT NULL,
  `monthly_budget` BIGINT NOT NULL,
  PRIMARY KEY (`goal_id`),
  KEY `idx_spending_goal_parent` (`goal_id`, `goal_type`),
  CONSTRAINT `chk_spending_goal_type`
    CHECK (`goal_type` = 'SPENDING'),
  CONSTRAINT `chk_spending_goal_monthly_budget`
    CHECK (`monthly_budget` > 0),
  CONSTRAINT `fk_spending_goal_goal`
    FOREIGN KEY (`goal_id`, `goal_type`)
    REFERENCES `goal` (`goal_id`, `type`)
);
