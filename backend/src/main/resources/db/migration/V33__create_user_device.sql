-- S15P21D105-580: API 22 Notification 푸시 기기 토큰. ERD docs/ERD/DODAM_ERDCLOUD_IMPORT.sql의 `user_device` 정의 그대로.
-- 번호 구간: V30~V49 BE-A 2차 구간(README "버전 번호 할당"). V32 notification 다음 번호다.
--
-- FCM 토큰은 계좌번호와 같은 규칙으로 암호문(`fcm_token_encrypted`, AES-256-GCM)과 조회용 해시(`fcm_token_hash`, HMAC-SHA-256)로만 저장한다.
-- 해시는 전체 UNIQUE라 같은 기기 토큰이 두 사용자에게 동시에 묶이지 않는다(나중에 로그인한 사용자가 가져간다).
-- `enabled = 0`은 FCM이 토큰 무효(UNREGISTERED)를 돌려줬거나 사용자가 해제한 기기다. 발송 대상은 enabled = 1만이다.

CREATE TABLE `user_device` (
  `device_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `fcm_token_encrypted` VARBINARY(1024) NOT NULL,
  `fcm_token_hash` CHAR(64) NOT NULL,
  `platform` VARCHAR(20) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `last_used_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `uk_user_device_fcm_token_hash` (`fcm_token_hash`),
  KEY `idx_user_device_user_id` (`user_id`),
  CONSTRAINT `chk_user_device_enabled`
    CHECK (`enabled` IN (0, 1)),
  CONSTRAINT `chk_user_device_platform`
    CHECK (`platform` IN ('ANDROID', 'IOS', 'WEB')),
  CONSTRAINT `fk_user_device_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
