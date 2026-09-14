-- S15P21D105-519/520: API 22 Notification. ERD docs/ERD/DODAM_ERDCLOUD_IMPORT.sql의 `notification` 정의 그대로.
-- 번호 구간: V30~V49 BE-A 2차 구간(README "버전 번호 할당"). V31 pattern_analysis 다음 번호다.
--
-- 알림은 도메인 이벤트(결제 예정·상환 예정·위험·추천·리포트 등, D10)가 NotificationPublisher로 만들고 이 API는 조회·읽음·삭제만 한다.
-- 연결 규약: source_type = 원천 도메인 코드(MINUS_ACCOUNT, CARD, LOAN, GOAL, SUBSCRIPTION, DIAGNOSIS, RECOMMENDATION, CASHFLOW_FORECAST, REPORT ...),
-- source_id = 그 도메인 PK의 문자열. (user_id, dedupe_key) 유일 제약이 같은 알림의 중복 생성을 막는다. 삭제는 deleted_at 소프트 삭제다.
-- `user_device`(FCM 토큰)는 푸시 발송(D10)에서 별도 마이그레이션으로 만든다.

CREATE TABLE `notification` (
  `notification_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `message` TEXT NOT NULL,
  `source_type` VARCHAR(50) NULL,
  `source_id` VARCHAR(100) NULL,
  `dedupe_key` CHAR(64) NULL,
  `is_read` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `read_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notification_user_dedupe` (`user_id`, `dedupe_key`),
  KEY `idx_notification_user_filters` (`user_id`, `is_read`, `type`, `created_at`),
  CONSTRAINT `fk_notification_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
