-- Jira: S15P21D105-499 (API 4 금융 데이터 동기화)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: 사용자별 금융 데이터 동기화 실행 이력 `finance_sync`.
-- 한 사용자에 RUNNING 행이 둘 이상 있으면 안 되지만(동시 Sync 중복 실행 방지) MySQL은 부분 유니크 인덱스를
-- 지원하지 않으므로, 애플리케이션이 `finance_connection` 행을 잠근 채(SELECT ... FOR UPDATE) 검사·삽입한다.

CREATE TABLE `finance_sync` (
  `sync_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `scope` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `progress` INT NULL,
  `error_message` VARCHAR(1000) NULL,
  `started_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`sync_id`),
  KEY `idx_finance_sync_user_id` (`user_id`),
  CONSTRAINT `chk_finance_sync_scope`
    CHECK (`scope` IN ('ALL', 'ACCOUNT', 'CARD', 'LOAN', 'SAVING')),
  CONSTRAINT `chk_finance_sync_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `chk_finance_sync_progress`
    CHECK (`progress` IS NULL OR `progress` BETWEEN 0 AND 100),
  CONSTRAINT `fk_finance_sync_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
