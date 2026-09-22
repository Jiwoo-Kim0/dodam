-- Jira: S15P21D105-697 (월간 리포트 주요 지표·금융 생활 패턴 서버 제공)
-- 월간 리포트에 "주요 지표"(저축률·고정지출 비중·카드 사용액·대출 잔액과 전월 비교)와 "금융 생활 패턴"(저축률·소비 증가·
-- 현금흐름 위험 3장) JSON을 저장하는 컬럼을 추가한다(응답 `MonthlyReport.metrics`).
--
-- FE 리포트 화면(605 협의안)이 개별 API 12개를 조합해 만들던 카드를 서버 값으로 바꾸기 위한 것이다. 효율 점수(V41)와 같은 시점에
-- 같은 입력(리포트 합계·고정지출·그 달 완료 진단·그 달 마지막 완료 예측)으로 계산해 굳히며, 대출 잔액만 이력이 없어 생성 시점 값이다.
-- 697 이전에 만들어진 READY 리포트는 이 컬럼이 NULL이며 다음 `/reports/monthly` 조회 때 합계는 그대로 두고 채운다
-- (`ReportService.supplementIfMissing`). 산식 버전은 JSON 안의 `calculationVersion`이다.
ALTER TABLE `monthly_report`
  ADD COLUMN `metrics_summary` JSON NULL AFTER `efficiency_summary`;
