-- Jira: S15P21D105-543 (API 14 Diagnosis 엔티티·Repository)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
--
-- 범위: Diagnosis 도메인이 소유하는 2테이블. 진단 실행·조회 API(544), 문제 목록·군집 근거(545),
-- 목표영향·진단변화(546)는 이 스키마 위에서 구현하며 여기서 테이블을 다시 만들지 않는다.
-- `recommendation`은 S15P21D105-547의 소관이라 포함하지 않는다.
--
-- 선행 테이블
-- ----------
-- `goal`은 BE-A 소관이지만 마이그레이션이 없어 V5로 스키마만 먼저 추가했다. 사유는 V5 주석 참고.
--
-- 집계 컬럼의 정합성
-- ------------------
-- `issue_count`와 `high_severity_count`를 헤더에 두는 것은 목록 조회가 매번 하위행을 세지 않게
-- 하기 위해서다. 대신 두 값이 실제 행 수와 어긋날 수 있으므로 `chk_diagnosis_high_severity_count`로
-- 상하한 관계만이라도 DB에서 막는다. 실제 채우는 책임은 진단 실행 경로에 있다.
--
-- 군집 보조 신호
-- -------------
-- 명세 14의 `detection_source`는 규칙 기반 진단과 군집(ML) 보조를 구분한다. 군집 결과가 없으면
-- 규칙 기반으로 진단한다는 것이 명세의 제한사항이므로 기본값을 'RULE'로 둔다. 군집 모델이 붙기 전
-- 저장되는 모든 행은 'RULE'이다.

CREATE TABLE `diagnosis` (
  `diagnosis_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `goal_id` BIGINT NULL,
  `issue_count` INT NOT NULL DEFAULT 0,
  `high_severity_count` INT NOT NULL DEFAULT 0,
  `overall_status` VARCHAR(30) NULL,
  `rule_version` VARCHAR(50) NULL,
  `input_snapshot` JSON NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`diagnosis_id`),
  UNIQUE KEY `uk_diagnosis_user_diagnosis_id` (`user_id`, `diagnosis_id`),
  KEY `idx_diagnosis_user_created_at` (`user_id`, `created_at`),
  KEY `idx_diagnosis_user_goal` (`user_id`, `goal_id`),
  CONSTRAINT `chk_diagnosis_overall_status`
    CHECK (`overall_status` IS NULL OR `overall_status` IN ('HEALTHY', 'CAUTION', 'RISK')),
  CONSTRAINT `chk_diagnosis_issue_count`
    CHECK (`issue_count` >= 0),
  CONSTRAINT `chk_diagnosis_high_severity_count`
    CHECK (`high_severity_count` >= 0 AND `high_severity_count` <= `issue_count`),
  CONSTRAINT `fk_diagnosis_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_diagnosis_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`)
);

CREATE TABLE `diagnosis_issue` (
  `issue_id` BIGINT NOT NULL AUTO_INCREMENT,
  `diagnosis_id` BIGINT NOT NULL,
  `domain` VARCHAR(50) NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `severity` VARCHAR(30) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  `current_value` JSON NULL,
  `benchmark_value` JSON NULL,
  `evidence` JSON NULL,
  `detection_source` VARCHAR(20) NOT NULL DEFAULT 'RULE',
  `model_version` VARCHAR(50) NULL,
  `cluster_context_json` JSON NULL,
  `detected_at` DATETIME NOT NULL,
  PRIMARY KEY (`issue_id`),
  KEY `idx_diagnosis_issue_filters` (`diagnosis_id`, `domain`, `type`, `severity`),
  CONSTRAINT `chk_diagnosis_issue_severity`
    CHECK (`severity` IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT `chk_diagnosis_issue_detection_source`
    CHECK (`detection_source` IN ('RULE', 'CLUSTER', 'RULE_AND_CLUSTER')),
  CONSTRAINT `fk_diagnosis_issue_diagnosis`
    FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnosis` (`diagnosis_id`)
);
