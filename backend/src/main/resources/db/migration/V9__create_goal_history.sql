-- Jira: S15P21D105-511 (API 12 목표 예측·위험·이력)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: 목표 변화 이력 `goal_history`. 목표 생성·수정·상태 변경·연결계좌 변경·확보 금액 갱신·삭제 시점의 스냅샷을 남긴다.
-- 이것으로 Goal API(12)의 테이블(V5, V7, V8, V9)이 모두 갖춰진다.

CREATE TABLE `goal_history` (
  `goal_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `goal_id` BIGINT NOT NULL,
  `recorded_date` DATE NOT NULL,
  `current_amount` BIGINT NOT NULL,
  `progress_rate` DECIMAL(10,4) NULL,
  `forecast_date` DATE NULL,
  `event` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`goal_history_id`),
  KEY `idx_goal_history_goal_id` (`goal_id`),
  CONSTRAINT `chk_goal_history_current_amount`
    CHECK (`current_amount` >= 0),
  CONSTRAINT `chk_goal_history_progress_rate`
    CHECK (`progress_rate` IS NULL OR `progress_rate` BETWEEN 0 AND 100),
  CONSTRAINT `fk_goal_history_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
);
