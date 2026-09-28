-- S15P21D105-679: 읽기 전용 적재 전후·재실행 검증. MySQL 8.x, 2026-03~09 시연 창.
-- 17번 적재 후 월별 FIX 10 / SAV 2 / INC 1(급여 3,012,400), 환급은 3월 1건이고 TRF(용돈)는 없다(2026-09-22 개정).
-- 보호 대상(타인 전체, U0003의 VAR·SUB)의 건수·합계·checksum은 적재 전후 같아야 한다. checksum은 회귀 확인용이다.
-- U0003 급여 INC-00 7건은 최초 적재에서 금액만 5,700,000 → 3,012,400으로 바뀌고(ID·날짜·시각 유지), 재실행에서는 변하지 않는다.
SET NAMES utf8mb4;
SET @demo_user_id = (SELECT user_id FROM `user` WHERE email = 'seed.kmeans.u0003@dodam.invalid');

SELECT CASE WHEN user_id <> @demo_user_id THEN 'OTHER_USERS' ELSE 'U0003_PROTECTED' END AS scope,
       COUNT(*) AS row_count, SUM(amount) AS amount_total,
       BIT_XOR(CRC32(CONCAT_WS('|', transaction_id, amount, transaction_date,
           transaction_time, merchant_name, spending_type, summary))) AS checksum
FROM `transaction`
WHERE user_id <> @demo_user_id OR (user_id = @demo_user_id
  AND (transaction_id LIKE '%-VAR-%' OR transaction_id LIKE '%-SUB-%'))
GROUP BY scope;

-- 급여 7건: 적재 후 amount_total 21,086,800, 최초 적재 전에는 39,900,000. ID·날짜 checksum은 전후 동일.
SELECT COUNT(*) AS salary_rows, SUM(amount) AS amount_total,
       BIT_XOR(CRC32(CONCAT_WS('|', transaction_id, transaction_date, transaction_time))) AS identity_checksum
FROM `transaction`
WHERE user_id = @demo_user_id AND transaction_id LIKE '%-INC-00';

SELECT DATE_FORMAT(transaction_date, '%Y-%m') AS month,
       SUBSTRING_INDEX(SUBSTRING_INDEX(transaction_id, '-', -2), '-', 1) AS kind,
       COUNT(*) AS row_count, SUM(amount) AS amount_total
FROM `transaction`
WHERE user_id = @demo_user_id AND source_type = 'SEED'
  AND transaction_date BETWEEN '2026-03-01' AND '2026-09-30'
GROUP BY month, kind ORDER BY month, kind;

-- 8월: 소비 2,559,221 / 고정 1,565,221 / 저축 이체 400,000.
-- 9월: 소비 2,522,221 / 고정 1,544,221 / 저축 이체 400,000.
SELECT DATE_FORMAT(transaction_date, '%Y-%m') AS month,
       SUM(CASE WHEN spending_type <> 'NON_SPENDING' OR spending_type IS NULL THEN amount ELSE 0 END) AS spending,
       SUM(CASE WHEN spending_type = 'FIXED' THEN amount ELSE 0 END) AS fixed,
       SUM(CASE WHEN spending_type = 'NON_SPENDING' THEN amount ELSE 0 END) AS saving_transfer
FROM `transaction`
WHERE user_id = @demo_user_id AND transaction_type = 'EXPENSE' AND hidden = 0
  AND transaction_date BETWEEN '2026-03-01' AND '2026-09-30'
GROUP BY month ORDER BY month;

-- 청구서 7건, bill_gap 전부 0. 10월 SCHEDULED 845,390원(선행 seed 현 버전 기준).
SELECT s.billing_month, s.status, s.total_balance,
       s.total_balance - COALESCE((SELECT SUM(t.amount) FROM `transaction` t
         WHERE t.user_id = c.user_id AND t.card_id = c.card_id
           AND t.transaction_type = 'EXPENSE' AND t.payment_method = 'CARD'
           AND t.transaction_date >= DATE_SUB(STR_TO_DATE(CONCAT(s.billing_month, '-01'), '%Y-%m-%d'), INTERVAL 1 MONTH)
           AND t.transaction_date < STR_TO_DATE(CONCAT(s.billing_month, '-01'), '%Y-%m-%d')), 0) AS bill_gap
FROM card_billing_statement s JOIN card c ON c.card_id = s.card_id
WHERE c.user_id = @demo_user_id ORDER BY s.billing_month;

-- 3,012,400 / 36,148,800 / 1,150,000 / 4,600,000
SELECT monthly_income, annual_income, fixed_expense, emergency_fund_target
FROM financial_profile WHERE user_id = @demo_user_id;

-- 정상이면 0. SQL 오류로 중단되면 작업 테이블이 남을 수 있어 재실행한다.
SELECT COUNT(*) AS scratch_table_count FROM information_schema.tables
WHERE table_schema = DATABASE() AND LEFT(table_name, 14) = 'tmp_dodam_679_';
