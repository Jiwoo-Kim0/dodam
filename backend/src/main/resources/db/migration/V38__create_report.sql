-- Jira: S15P21D105-590 (API 20 Report 저장 기반)
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql `report`/`daily_report`/`monthly_report` (2026-09-16 개정본)
--
-- 범위: 리포트 상위행 1개와 유형별 하위행 2개. 상위·하위는 한 트랜잭션에서 함께 만든다(README 무결성 규칙).
-- `(user_id, type, period)` UNIQUE가 같은 기간 리포트의 중복 생성을 막는다. 생성은 조회 시점에 한 번만 일어나고
-- (배치 스케줄러가 없다) 이미 저장된 기간은 저장본을 그대로 돌려준다.
--
-- ERD 대비 추가 컬럼
-- ----------------
-- 공통 `data_status` : 기간은 끝났지만 값을 못 만든 경우와 정상 생성을 구분한다. FE 협의안(docs/report-backend-handoff.md
--                      3번)의 "리포트 미생성 / 거래 0건 / 일부 지표 계산 불가"를 404 하나로 표현하지 않기 위한 컬럼이다.
-- daily_report.financial_summary : 응답의 합계·상위 카테고리. ERD에는 저장 위치가 없었다.
-- daily_report.notable_events    : 그날 일어난 일. ERD의 `upcoming_events`는 "앞으로 예정"이라 의미가 다르다.
-- daily_report.coach_message     : API 21 문장을 넣지 않는다(계약 3-5). 브리핑 전용 문구가 생길 자리이며 당분간 NULL이다.
-- daily_report.available_today   : ERD는 NOT NULL이지만 브리핑은 "전날에 대한" 요약이라 조회 시점의 가용액을 과거
--                                  리포트에 굳혀 둘 근거가 없다. NULL 허용으로 바꾸고 계산되면 채운다.
-- monthly_report.diagnosis_id / overall_status : 응답 계약의 필드. JSON에 묻으면 조회 조건으로 쓸 수 없다.
-- 위 항목은 docs/ERD/DODAM_ERDCLOUD_IMPORT.sql에도 함께 반영했다.
--
-- 하위 응답이 리포트 기반인 이유
-- ---------------------------
-- 월간 소비패턴·목표변화·개선행동 요약은 월간 리포트를 만들 때 계산해 JSON으로 함께 저장하고, 각 엔드포인트는
-- 저장본을 읽는다. 같은 달을 두 경로로 다시 집계하면 합계가 어긋날 수 있다.
-- 다음달 예정비용만 예외로 저장본을 쓰지 않는다 — 미래 스케줄이라 생성 시점에 굳히면 바로 낡는다.

CREATE TABLE `report` (
  `report_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `period` VARCHAR(20) NOT NULL,
  `period_start` DATE NOT NULL,
  `period_end` DATE NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_id_type` (`report_id`, `type`),
  UNIQUE KEY `uk_report_user_type_period` (`user_id`, `type`, `period`),
  KEY `idx_report_user_period_range` (`user_id`, `period_start`, `period_end`),
  CONSTRAINT `chk_report_type`
    CHECK (`type` IN ('DAILY', 'MONTHLY')),
  CONSTRAINT `chk_report_period_dates`
    CHECK (`period_start` <= `period_end`),
  CONSTRAINT `fk_report_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `daily_report` (
  `report_id` BIGINT NOT NULL,
  `report_type` VARCHAR(20) NOT NULL,
  `report_date` DATE NOT NULL,
  `data_status` VARCHAR(30) NOT NULL,
  `summary` TEXT NULL,
  `available_today` BIGINT NULL,
  `financial_summary` JSON NULL,
  `notable_events` JSON NULL,
  `upcoming_events` JSON NULL,
  `risks` JSON NULL,
  `recommendations` JSON NULL,
  `coach_message` TEXT NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_daily_report_parent` (`report_id`, `report_type`),
  CONSTRAINT `chk_daily_report_type`
    CHECK (`report_type` = 'DAILY'),
  CONSTRAINT `chk_daily_report_data_status`
    CHECK (`data_status` IN ('READY', 'NO_TRANSACTIONS', 'INSUFFICIENT_DATA')),
  CONSTRAINT `fk_daily_report_report`
    FOREIGN KEY (`report_id`, `report_type`)
    REFERENCES `report` (`report_id`, `type`)
);

CREATE TABLE `monthly_report` (
  `report_id` BIGINT NOT NULL,
  `report_type` VARCHAR(20) NOT NULL,
  `year_month` CHAR(7) NOT NULL,
  `data_status` VARCHAR(30) NOT NULL,
  `diagnosis_id` BIGINT NULL,
  `overall_status` VARCHAR(20) NULL,
  `financial_summary` JSON NULL,
  `spending_summary` JSON NULL,
  `goal_summary` JSON NULL,
  `improvement_summary` JSON NULL,
  `upcoming_summary` JSON NULL,
  `llm_comment` TEXT NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_monthly_report_parent` (`report_id`, `report_type`),
  CONSTRAINT `chk_monthly_report_type`
    CHECK (`report_type` = 'MONTHLY'),
  CONSTRAINT `chk_monthly_report_data_status`
    CHECK (`data_status` IN ('READY', 'NO_TRANSACTIONS', 'INSUFFICIENT_DATA')),
  CONSTRAINT `fk_monthly_report_report`
    FOREIGN KEY (`report_id`, `report_type`)
    REFERENCES `report` (`report_id`, `type`)
);
