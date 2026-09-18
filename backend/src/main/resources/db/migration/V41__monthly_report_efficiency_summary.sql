-- Jira: S15P21D105-635 (리포트 금융 효율 점수 및 K-Means 또래 비교 요청 개선)
-- 월간 리포트에 금융 효율 점수(전체 + 6개 항목)를 저장하는 JSON 컬럼을 추가한다.
--
-- 점수는 그 달의 소비·저축·고정지출 합계, 사용자 신용등급, 그 달 안의 현금흐름 예측 위험, 그 달 완료 진단의 문제로
-- 만들며 리포트 생성 시점에 한 번 계산해 굳힌다(응답 `MonthlyReport.efficiency`). 산식 버전은 JSON 안의
-- `calculationVersion`이다. 635 이전에 만들어진 READY 리포트는 이 컬럼이 NULL이며, 다음 `/reports/monthly` 조회 때
-- 저장된 합계는 그대로 두고 이 컬럼과 카드 사용액만 채운다(`ReportService.supplement`).
--
-- 카드 사용액(`cardSpending`·전월 대비 증감)은 별도 컬럼이 아니라 `financial_summary` JSON의 필드로 함께 저장한다.
ALTER TABLE `monthly_report`
  ADD COLUMN `efficiency_summary` JSON NULL AFTER `improvement_summary`;
