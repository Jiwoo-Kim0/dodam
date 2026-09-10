-- Jira: S15P21D105-543 (API 14 Diagnosis 엔티티·Repository)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 왜 BE-B 이슈가 BE-A 구간(V1~V9)의 테이블을 만드는가
-- --------------------------------------------------
-- `diagnosis.goal_id`는 ERD상 `goal(user_id, goal_id)`를 복합 외래키로 참조한다. `goal`
-- 마이그레이션이 아직 없어 이 테이블 없이는 V17의 `fk_diagnosis_goal`을 만들 수 없다.
-- V3의 `account`와 같은 처리다. 스키마만 먼저 추가하고 Goal API(S15P21D105-508~511)는 BE-A에서
-- 진행한다. **BE-A는 같은 테이블을 다시 만드는 마이그레이션을 추가하지 않는다.**
--
-- FK를 빼고 나중에 추가하는 방식은 택하지 않았다. 데이터가 적재된 뒤에는 제약 추가가 어렵다
-- (db/migration/README.md의 card·loan 결정과 같은 이유).
--
-- 범위
-- ----
-- `goal` 한 테이블만 만든다. `saving_goal`·`spending_goal`·`goal_account` 하위행은 Goal API가
-- 소유하며 diagnosis가 참조하지 않는다. V3이 `account`만 만들고 상세 테이블을 두지 않은 것과 같다.

CREATE TABLE `goal` (
  `goal_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(150) NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `current_amount` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`goal_id`),
  KEY `idx_goal_user_id` (`user_id`),
  UNIQUE KEY `uk_goal_user_goal_id` (`user_id`, `goal_id`),
  UNIQUE KEY `uk_goal_id_type` (`goal_id`, `type`),
  CONSTRAINT `chk_goal_type`
    CHECK (`type` IN ('SAVING', 'SPENDING')),
  CONSTRAINT `chk_goal_status`
    CHECK (`status` IN ('ACTIVE', 'ACHIEVED', 'PAUSED', 'CANCELED')),
  CONSTRAINT `chk_goal_current_amount`
    CHECK (`current_amount` >= 0),
  CONSTRAINT `fk_goal_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
