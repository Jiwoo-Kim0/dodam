-- 소셜 로그인(카카오·구글) 공급자별 사용자 식별정보. ERD docs/ERD/DODAM_ERDCLOUD_IMPORT.sql의 `user_oauth_account` 정의 그대로.
-- 번호 구간: V30~V49 BE-A 2차 구간(README "버전 번호 할당"). V33 user_device 다음 번호다.
--
-- 공급자 사용자 ID는 원문을 저장하지 않고 HMAC-SHA-256 해시(`provider_user_id_hash`)로만 둔다(계좌번호·FCM 토큰과 같은 규칙).
-- 공급자가 준 이메일은 암호문(`provider_email_encrypted`, AES-256-GCM)·조회 해시·마스킹으로 저장한다. 이메일을 주지 않는 공급자·동의 거부 시 NULL.
-- (provider, provider_user_id_hash) UNIQUE: 한 소셜 계정은 한 사용자에게만 연결된다.
-- (user_id, provider) UNIQUE: 한 사용자는 공급자별로 소셜 계정 하나만 연결한다.

CREATE TABLE `user_oauth_account` (
  `oauth_account_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `provider` VARCHAR(30) NOT NULL,
  `provider_user_id_hash` CHAR(64) NOT NULL,
  `provider_email_encrypted` VARBINARY(512) NULL,
  `provider_email_hash` CHAR(64) NULL,
  `provider_email_masked` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`oauth_account_id`),
  UNIQUE KEY `uk_user_oauth_provider_user` (`provider`, `provider_user_id_hash`),
  UNIQUE KEY `uk_user_oauth_user_provider` (`user_id`, `provider`),
  CONSTRAINT `chk_user_oauth_account_provider`
    CHECK (`provider` IN ('KAKAO', 'GOOGLE')),
  CONSTRAINT `fk_user_oauth_account_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
