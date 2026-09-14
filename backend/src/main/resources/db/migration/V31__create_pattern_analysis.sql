-- S15P21D105-516/517/518: API 3 Preference(사용자 패턴 분석). ERD docs/ERD/DODAM_ERDCLOUD_IMPORT.sql의 `pattern_analysis` 정의 그대로.
-- 번호 구간: V30~V49 BE-A 2차 구간(README "버전 번호 할당"). V30 subscription 다음 번호다.
--
-- 한 행이 분석 실행 1건이다. 규칙 기반 지표 8종(spending_regularity ~ liquidity_level)은 거래·계좌에서 계산하고,
-- 군집 컬럼(model_version ~ confidence)은 HYBRID 모드에서 표본·신뢰도 조건을 충족했을 때만 채운다(그 외 NULL).
-- 군집 통계는 익명 집계값만 저장하며 타 사용자 식별정보·거래 원문은 넣지 않는다(기획안 14-6).

CREATE TABLE `pattern_analysis` (
  `analysis_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `lookback_months` INT NOT NULL,
  `data_point_count` INT NOT NULL,
  `spending_regularity` DECIMAL(10,4) NULL,
  `saving_consistency` DECIMAL(10,4) NULL,
  `fixed_expense_ratio` DECIMAL(10,4) NULL,
  `variable_expense_ratio` DECIMAL(10,4) NULL,
  `discretionary_spending_ratio` DECIMAL(10,4) NULL,
  `debt_burden` DECIMAL(10,4) NULL,
  `cashflow_stability` DECIMAL(10,4) NULL,
  `liquidity_level` VARCHAR(30) NULL,
  `model_version` VARCHAR(50) NULL,
  `cluster_id` INT NULL,
  `cluster_label` VARCHAR(50) NULL,
  `feature_window_months` INT NULL,
  `feature_summary_json` JSON NULL,
  `cluster_stats_json` JSON NULL,
  `sample_size` INT NULL,
  `confidence` DECIMAL(5,4) NULL,
  `analyzed_at` DATETIME NOT NULL,
  PRIMARY KEY (`analysis_id`),
  KEY `idx_pattern_analysis_user_id` (`user_id`),
  CONSTRAINT `chk_pattern_analysis_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `chk_pattern_analysis_lookback_months`
    CHECK (`lookback_months` > 0),
  CONSTRAINT `chk_pattern_analysis_data_point_count`
    CHECK (`data_point_count` >= 0),
  CONSTRAINT `chk_pattern_analysis_feature_window_months`
    CHECK (`feature_window_months` IS NULL OR `feature_window_months` > 0),
  CONSTRAINT `chk_pattern_analysis_sample_size`
    CHECK (`sample_size` IS NULL OR `sample_size` >= 0),
  CONSTRAINT `chk_pattern_analysis_confidence`
    CHECK (`confidence` IS NULL OR `confidence` BETWEEN 0 AND 1),
  CONSTRAINT `fk_pattern_analysis_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
