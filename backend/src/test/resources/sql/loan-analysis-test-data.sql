-- S15P21D105-532: loan-query-test-data.sql 뒤에 실행하는 격리 H2 분석 fixture.
-- 매 테스트에서 기반 fixture를 재생성한 후 동일 행만 갱신하므로 반복 실행 가능하다.
-- 기준일 2026-09-09, 사용자 1의 3회 원금균등/만기일시 대출 총 예상 이자는 60,000원이다.
-- 실제 마이그레이션이나 운영·시연 데이터에는 적용하지 않는다.
DELETE FROM loan WHERE loan_id = 5;
UPDATE loan SET original_principal = 1200000, balance = 1200000, interest_rate = 12,
    next_repayment_date = DATE '2026-09-09', maturity_date = DATE '2026-11-09', repayment_day = 9
WHERE loan_id IN (1, 2, 3);
DELETE FROM financial_profile WHERE user_id IN (1, 2);
INSERT INTO financial_profile (user_id, monthly_income, updated_at)
VALUES (1, 2800000, TIMESTAMP '2026-09-09 00:00:00');
