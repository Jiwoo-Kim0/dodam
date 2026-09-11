-- Jira: S15P21D105-509 (API 12 목표 연결계좌)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: 모으기 목표에 연결된 계좌 `goal_account`. `goal_history`는 511(변화 이력)에서 추가한다.
--
-- 선행 조건: `goal(user_id, goal_id)`(V5), `saving_goal(goal_id)`(V7), `account(user_id, account_id, account_type)`(V3)를
-- 복합 외래키로 참조한다. `fk_goal_account_saving_goal` 때문에 줄이기(SPENDING) 목표에는 계좌를 연결할 수 없고,
-- `uk_goal_account_account` 때문에 한 계좌는 한 목표에만 연결된다.

CREATE TABLE `goal_account` (
  `user_id` BIGINT NOT NULL,
  `goal_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `contribution_amount` BIGINT NULL,
  PRIMARY KEY (`goal_id`, `account_id`),
  UNIQUE KEY `uk_goal_account_account` (`account_id`),
  KEY `idx_goal_account_user_goal` (`user_id`, `goal_id`),
  KEY `idx_goal_account_user_account` (`user_id`, `account_id`, `account_type`),
  CONSTRAINT `chk_goal_account_type`
    CHECK (`account_type` IN ('DEMAND_DEPOSIT', 'DEPOSIT', 'SAVINGS')),
  CONSTRAINT `chk_goal_account_contribution_amount`
    CHECK (`contribution_amount` IS NULL OR `contribution_amount` >= 0),
  CONSTRAINT `fk_goal_account_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`),
  CONSTRAINT `fk_goal_account_saving_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `saving_goal` (`goal_id`),
  CONSTRAINT `fk_goal_account_account`
    FOREIGN KEY (`user_id`, `account_id`, `account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);
