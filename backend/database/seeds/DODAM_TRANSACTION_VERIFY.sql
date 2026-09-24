-- ---------------------------------------------------------------------------
-- DODAM_TRANSACTION_VERIFY.sql — S15P21D105-706
-- 24개월 거래 창(2024-09-01 ~ 2026-08-31)이 제대로 만들어졌는지, 그리고
-- 기존 6개월(2026-03-01 ~ 2026-08-31)이 한 행도 변하지 않았는지 확인한다.
--
-- 선행: 6(492) · 13(633) · 14(636) · 17(679)까지 적재한 뒤 실행한다.
-- 읽기 전용이다. 어떤 행도 만들거나 고치지 않는다.
--
-- 왜 "불변"을 따로 재는가
-- ----------------------
-- 492는 금액·날짜·시각·카테고리·결제수단을 전부
--   h = sequence_no * 7919 + month_no * 104729 + slot_no * 15485863
-- 에서 뽑는다. month_no를 2024-09부터 다시 매기면 기존 6개월이 통째로 다른
-- 데이터가 되고, 678·679가 굳힌 2026-08 시연 계약과 K-Means 특징 창이 같이
-- 깨진다. 그래서 과거 18개월에는 7~24를 주는데, 이 번호는 직관에 어긋나
-- 나중에 "정리"당하기 쉽다. 아래 1번이 그 방어선이다.
-- ---------------------------------------------------------------------------
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- 1. 기존 6개월 불변 — 이 파일의 핵심 검사.
--    시연 사용자 U0001~U0004는 636·655·679가 행을 다시 만들어 건수가 달라지므로 제외한다.
--    나머지 3,996명의 2026-03~08 행은 492만 만들고 아무도 건드리지 않아 값이 고정된다.
--    Expected: 347652 / 195906782800 / 499687287  (2026-09-24 로컬 MySQL 8.0 실측)
SELECT COUNT(*) AS existing_window_rows, SUM(tx.amount) AS existing_window_amount,
  BIT_XOR(CRC32(CONCAT_WS('|', tx.transaction_id, tx.transaction_date, tx.transaction_time, tx.amount,
    tx.category_id, COALESCE(tx.account_id,'-'), COALESCE(tx.card_id,'-'), tx.payment_method,
    tx.merchant_name))) AS existing_window_fingerprint
FROM `transaction` tx
JOIN `user` u ON u.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-U%'
  AND u.email NOT IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                      'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid')
  AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-08-31';

-- 2. 전체 창 (시연 4명 제외). Expected: 1390608, first 2024-09-xx, last 2026-08-xx
SELECT COUNT(*) AS total_rows, MIN(tx.transaction_date) AS first_date, MAX(tx.transaction_date) AS last_date
FROM `transaction` tx
JOIN `user` u ON u.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-U%'
  AND u.email NOT IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                      'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid');

-- 3. 종류별 (전체). 492 직후라면 INC 96000 / FIX 288000 / SUB 144000 / SAV 96000 / VAR 768768,
--    체인 전체를 돌린 뒤에는 시연 4명 몫이 636·655·679 값으로 바뀌어 조금 늘어난다.
SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(transaction_id, '-', 4), '-', -1) AS kind, COUNT(*) AS row_count
FROM `transaction`
WHERE source_type = 'SEED' AND transaction_id LIKE 'SEED-U%'
GROUP BY kind ORDER BY kind;

-- 4. 월 누락. Expected: 24 (창 안 모든 달에 거래가 있다)
SELECT COUNT(DISTINCT DATE_FORMAT(transaction_date, '%Y%m')) AS month_count
FROM `transaction`
WHERE source_type = 'SEED' AND transaction_id LIKE 'SEED-U%'
  AND transaction_date BETWEEN '2024-09-01' AND '2026-08-31';

-- 5. U0003 급여 연속성. Expected: 25행, 2025-08까지 2900000, 2025-09부터 3012400, 5700000 없음
SELECT DATE_FORMAT(tx.transaction_date, '%Y-%m') AS ym, tx.amount
FROM `transaction` tx
JOIN `user` u ON u.user_id = tx.user_id AND u.email = 'seed.kmeans.u0003@dodam.invalid'
WHERE tx.source_type = 'SEED' AND tx.transaction_type = 'INCOME'
  AND (tx.transaction_id LIKE 'SEED-U0003-%-INC-00' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-INC-00')
ORDER BY tx.transaction_date;

-- 6. U0003 월별 변동 소비. Expected: 6.2.1 설계표와 일치, 2026-03~09는 기존값
--    (974,000 / 939,000 / 1,653,000 / 1,015,000 / 1,031,000 / 994,000 / 978,000)
SELECT DATE_FORMAT(tx.transaction_date, '%Y-%m') AS ym, SUM(tx.amount) AS variable_total, COUNT(*) AS tx_count
FROM `transaction` tx
JOIN `user` u ON u.user_id = tx.user_id AND u.email = 'seed.kmeans.u0003@dodam.invalid'
WHERE tx.source_type = 'SEED' AND tx.spending_type = 'VARIABLE'
GROUP BY ym ORDER BY ym;

-- 7. U0003 고정비·저축 이체. Expected: FIX 249, SAV 36, loan_before_first_instalment 0
SELECT
  SUM(tx.transaction_id LIKE '%-FIX-%') AS fixed_rows,
  SUM(tx.transaction_id LIKE '%-SAV-%') AS saving_rows,
  SUM(tx.transaction_id LIKE '%-FIX-10' AND tx.transaction_date < '2024-10-01') AS loan_before_first_instalment
FROM `transaction` tx
JOIN `user` u ON u.user_id = tx.user_id AND u.email = 'seed.kmeans.u0003@dodam.invalid'
WHERE tx.source_type = 'SEED';

-- 8. 대출 상환 원천. Expected: 25 (PAID 24 + SCHEDULED 1), 잔액 12,998,492
SELECT COUNT(*) AS repayment_rows,
       SUM(r.status = 'PAID') AS paid_rows,
       MIN(r.repayment_date) AS first_date,
       MAX(r.repayment_date) AS last_date
FROM loan_repayment r
JOIN loan l ON l.loan_id = r.loan_id
JOIN `user` u ON u.user_id = l.user_id AND u.email = 'seed.kmeans.u0003@dodam.invalid';

-- 9. 계약 위반. Expected: 0
SELECT COUNT(*) AS contract_violation_count
FROM `transaction` tx
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-U%'
  AND (tx.transaction_date NOT BETWEEN '2024-09-01' AND '2026-09-30'
       OR tx.amount <= 0
       OR tx.category_id IS NULL
       OR (tx.account_id IS NULL AND tx.card_id IS NULL)
       OR (tx.account_id IS NOT NULL AND tx.card_id IS NOT NULL));
