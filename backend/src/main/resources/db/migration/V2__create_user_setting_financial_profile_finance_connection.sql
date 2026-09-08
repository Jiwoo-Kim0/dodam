-- Jira: S15P21D105-494 (금융망 연결), S15P21D105-497 (사용자 설정·금융프로필)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: User API가 다루는 `user_setting`, `financial_profile`과
-- 회원가입 시 SSAFY 금융망 userKey를 보관하는 `finance_connection`.

CREATE TABLE `user_setting` (
  `user_id` BIGINT NOT NULL,
  `notification_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `daily_briefing_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `monthly_report_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `daily_briefing_time` TIME NULL,
  `timezone` VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `chk_user_setting_flags`
    CHECK (`notification_enabled` IN (0, 1)
       AND `daily_briefing_enabled` IN (0, 1)
       AND `monthly_report_enabled` IN (0, 1)),
  CONSTRAINT `fk_user_setting_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `financial_profile` (
  `user_id` BIGINT NOT NULL,
  `monthly_income` BIGINT NULL,
  `annual_income` BIGINT NULL,
  `payday` INT NULL,
  `fixed_expense` BIGINT NULL,
  `emergency_fund_target` BIGINT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `chk_financial_profile_monthly_income`
    CHECK (`monthly_income` IS NULL OR `monthly_income` >= 0),
  CONSTRAINT `chk_financial_profile_annual_income`
    CHECK (`annual_income` IS NULL OR `annual_income` >= 0),
  CONSTRAINT `chk_financial_profile_payday`
    CHECK (`payday` IS NULL OR `payday` BETWEEN 1 AND 31),
  CONSTRAINT `chk_financial_profile_fixed_expense`
    CHECK (`fixed_expense` IS NULL OR `fixed_expense` >= 0),
  CONSTRAINT `chk_financial_profile_emergency_fund_target`
    CHECK (`emergency_fund_target` IS NULL OR `emergency_fund_target` >= 0),
  CONSTRAINT `fk_financial_profile_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `finance_connection` (
  `connection_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `member_email_encrypted` VARBINARY(512) NULL,
  `member_email_hash` CHAR(64) NULL,
  `member_email_masked` VARCHAR(255) NULL,
  `external_user_key_encrypted` VARBINARY(512) NULL,
  `external_user_key_hash` CHAR(64) NULL,
  `status` VARCHAR(30) NOT NULL,
  `connected_at` DATETIME NULL,
  `disconnected_at` DATETIME NULL,
  `last_synced_at` DATETIME NULL,
  PRIMARY KEY (`connection_id`),
  UNIQUE KEY `uk_finance_connection_user_id` (`user_id`),
  UNIQUE KEY `uk_finance_connection_member_email_hash` (`member_email_hash`),
  UNIQUE KEY `uk_finance_connection_external_user_key_hash` (`external_user_key_hash`),
  CONSTRAINT `chk_finance_connection_status`
    CHECK (`status` IN ('PENDING', 'CONNECTED', 'DISCONNECTED', 'FAILED')),
  CONSTRAINT `fk_finance_connection_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
