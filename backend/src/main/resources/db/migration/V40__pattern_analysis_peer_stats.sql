-- Jira: S15P21D105-632 (k-means 클러스터 결과 Report 개선 — 군집 내 위치·동년배 비교)
-- Schema basis: V31 pattern_analysis, V39 user.birth_date / gender.
--
-- 범위: 분석 완료 시점에 계산한 동년배 비교(나이대·성별·나이대×성별·군집×나이대 네 축)를 차원별 JSON으로 굳혀 둔다.
-- cluster_stats_json과 같은 이유다 — 조회 때 재계산하지 않아 모델을 다시 학습해도 과거 분석 결과가 바뀌지 않고,
-- AI 코치가 모델·인구통계를 다시 읽지 않고 그대로 옮긴다. 나이대는 분석 시점 나이로 굳힌다.
--
-- NULL 허용: 632 이전 분석, 규칙 기반(RULE_ONLY) 분석, 군집이 배정되지 않은 분석은 값이 없다.
-- 조회 API는 NULL이면 unavailableReason = NOT_COMPUTED / CLUSTER_NOT_ASSIGNED로 응답한다.

ALTER TABLE `pattern_analysis`
  ADD COLUMN `peer_stats_json` JSON NULL AFTER `cluster_stats_json`;
