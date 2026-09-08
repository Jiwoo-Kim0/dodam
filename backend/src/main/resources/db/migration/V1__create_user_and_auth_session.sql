-- Jira: S15P21D105-494 (API 1 회원가입·로그인·세션조회)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Auth API가 필요한 `user`, `auth_session` 두 테이블만 생성한다.
-- `user_setting`, `financial_profile`, `account` 등 나머지 ERD 테이블은
-- 해당 도메인 티켓에서 다음 버전 마이그레이션으로 추가한다.
-- Seed(backend/database/seeds)는 이 마이그레이션 이후 적재 순서를 지켜야 한다.

CREATE TABLE `user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `password_hash` VARCHAR(255) NULL,
  `name` VARCHAR(100) NOT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_email` (`email`),
  CONSTRAINT `chk_user_role`
    CHECK (`role` IN ('USER', 'ADMIN'))
);

CREATE TABLE `auth_session` (
  `session_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `refresh_token_hash` VARCHAR(255) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `created_at` DATETIME NOT NULL,
  `last_accessed_at` DATETIME NULL,
  `revoked_at` DATETIME NULL,
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_auth_session_refresh_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_session_user_id` (`user_id`),
  CONSTRAINT `fk_auth_session_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
