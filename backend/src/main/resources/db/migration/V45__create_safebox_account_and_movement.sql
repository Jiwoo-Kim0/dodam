-- Jira: S15P21D105-705. 사용자당 도담 전용 계좌 하나와 목표별 내부 이동 원장.
-- 기존 goal_account의 NULL 입출금 연결은 자동 전환하지 않는다. 전액 연결을 임의의 배정액으로 바꾸면 기존 목표 금액이 왜곡된다.
CREATE TABLE `safebox_account` (
  `user_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `account_type` VARCHAR(30) NOT NULL DEFAULT 'DEMAND_DEPOSIT',
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_safebox_account_id` (`account_id`),
  CONSTRAINT `chk_safebox_account_type` CHECK (`account_type` = 'DEMAND_DEPOSIT'),
  CONSTRAINT `fk_safebox_account_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_safebox_account_account` FOREIGN KEY (`user_id`, `account_id`, `account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

-- 금융망 개설 결과가 불명확하면 이 기록을 남겨 자동 재개설을 막는다.
CREATE TABLE `safebox_open_attempt` (
  `user_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_safebox_open_attempt_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `safebox_movement` (
  `movement_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `goal_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `amount_before` BIGINT NOT NULL,
  `amount_after` BIGINT NOT NULL,
  `delta` BIGINT NOT NULL,
  `occurred_at` DATETIME NOT NULL,
  PRIMARY KEY (`movement_id`),
  KEY `idx_safebox_movement_goal_date` (`goal_id`, `occurred_at`),
  CONSTRAINT `chk_safebox_movement_amount` CHECK (`amount_before` >= 0 AND `amount_after` >= 0),
  CONSTRAINT `fk_safebox_movement_goal` FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`),
  CONSTRAINT `fk_safebox_movement_account` FOREIGN KEY (`user_id`, `account_id`)
    REFERENCES `account` (`user_id`, `account_id`)
);
