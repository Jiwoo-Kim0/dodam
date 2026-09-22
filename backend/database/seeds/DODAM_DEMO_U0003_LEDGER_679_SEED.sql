-- DODAM demo user U0003 ("김싸피") ledger realism seed: itemised fixed expenses, savings transfers, salary, card bills
-- Jira: S15P21D105-679 (스프린트 312 — 지출·고정지출·현금흐름 시연 데이터 보강)
-- Dialect: MySQL 8.x
-- Schema basis: V2 (financial_profile), V6 (transaction), V11 (card_billing_statement), V13 (loan_repayment), V15 (savings_payment)
-- Dependency: 491 (user/account/profile), 522 (primary card), 492 + 625 (INC/FIX/SAV rows), 633 (loan + repayments),
--             636 (variable story), 655 (subscriptions), 678 rev. 2026-09-22 (two ACTIVE savings accounts paying on the 15th
--             + one matured account without transfers)
-- Dataset: U0003 only. Fixed 70 rows (10 items x 7 months), savings transfers 14, tax refund 1, salary amount override on
--          the 7 existing INC-00 rows, card billing statements 7 (PAID 6 + SCHEDULED 1),
--          financial_profile monthly_income / annual_income / fixed_expense / emergency_fund_target.
-- Snapshot: 2026-09-22 (planning: backend/docs/demo-ledger-cashflow-679-plan.md; revised the same day: salary 3,012,400,
--          no allowance row, savings 400,000/month — see the plan's revision note)
--
-- Why this file exists
-- --------------------
-- 492 gives U0003 a 5,700,000 salary (491 formula: 2,300,000 + MOD(2 * 17, 38) * 100,000) and three synthetic fixed rows a month
-- (주거 2,371,200 · 통신공과금 ~73만 · 보험 547,200 = 3,648,000) plus a generic 1,710,000 saving transfer, while the loan instalment
-- (633) and the 678 savings instalments never appear in the ledger. The fixed rows look nothing like a real bank statement.
-- This file rebuilds U0003's non-variable ledger so that:
--   - the salary is 3,012,400 (decision 2026-09-22: a modest earner whose fixed costs weigh ~52% of income), written on the
--     7 existing INC-00 rows and on financial_profile.monthly_income / annual_income (491 formula value is overwritten)
--   - fixed expenses are itemised like a 43-year-old single tenant's statement (월세·관리비·정수기·통신·전기·가스·보험·대출)
--   - savings transfers equal the 678 savings_payment rows (300,000 + 100,000 on the 15th; the matured 목돈적금 has none)
--   - the loan instalment (410,331 on the 10th) matches the 633 loan_repayment PAID rows
--   - the card gets billing statements whose totals are the ledger's own card charges (no hard-coded amounts)
--   - financial_profile.fixed_expense matches the new ledger (loan excluded: InstallmentAvailableFundsReader subtracts it separately)
-- Budget check (Aug): salary 3,012,400 - spending 2,559,221 (fixed 1,565,221 incl. subs + variable 994,000) - savings 400,000
-- = +53,179. May (travel month, variable 1,618,000) is the only deficit month by design.
-- Variable rows (636) and subscription rows (655) are not touched; INC-00 rows keep their ids, dates and times.
--
-- Monthly plan (won)                       HOUSING   UTILITIES  INSURANCE  LOAN     fixed total   (subs 104,390 · variable per 636)
-- ----------------------------------------------------------------------------------------------------------------------------
--   2026-03  winter gas, mgmt fee high      710,900   207,200    150,400    410,331   1,478,831
--   2026-04                                 697,900   185,200    150,400    410,331   1,443,831
--   2026-05                                 690,900   174,200    150,400    410,331   1,425,831
--   2026-06                                 694,900   174,200    150,400    410,331   1,429,831
--   2026-07  electricity rising             703,900   184,200    150,400    410,331   1,448,831
--   2026-08  electricity peak               708,900   191,200    150,400    410,331   1,460,831
--   2026-09                                 700,900   178,200    150,400    410,331   1,439,831
-- 627 CATEGORY_SPENDING_INCREASE guard (Aug vs avg May..Jul, > 20% and >= 10,000): HOUSING +1.8%, UTILITIES +7.7%, others 0%
-- -> the diagnosis keeps the 636 outcome (FOOD, CAFE, high-interest loan). No ETC row is added here (allowance dropped 2026-09-22).
--
-- Rows written per month
-- ----------------------
--   FIX-01..10  EXPENSE / FIXED / ACCOUNT, categories HOUSING·UTILITIES·INSURANCE·LOAN_REPAYMENT, early-morning auto-debit times
--   SAV-01..02  EXPENSE / NON_SPENDING / SAVING, 15th 09:35~09:36, merchant = savings account name (678)
--   INC-01      INCOME / NON_SPENDING, 2026-03-20 only, 412,350 "연말정산 환급금"
--   INC-00      not written here; the 492/625 rows are kept and only their amount is set to 3,012,400
--   TRF-01      no longer written (allowance to parents, dropped 2026-09-22); the delete below still removes rows of the
--               earlier revision so a DB loaded with it converges
--   ids: SEED-U0003-2026MM-<KIND>-nn (Mar..Aug), SEED-SEP-U0003-202609-<KIND>-nn (Sep) so 492 / 625 reruns delete them too
--
-- Card billing statements (card_billing_statement, PK card_id + billing_month)
-- ---------------------------------------------------------------------------
--   usage month M (Mar..Sep) -> billing_month M+1, billing_date = 15th (card.withdrawal_day), total_balance = SUM of U0003's
--   card EXPENSE rows in M. Apr..Sep statements are PAID on billing_date; Oct is SCHEDULED (payment_date NULL).
--   No card-bill withdrawal row is written to `transaction`: card approvals are already counted as spending.
--
-- Rerun / ordering contract
-- -------------------------
-- - Run AFTER 6번(492), 8번, 11번(625), 13번(633), 14번(636), 15번(655), 16번(678). Rerunning this file alone is idempotent
--   (deletes and regenerates its own rows; statements are deleted by (card, month) and rebuilt; profile values are overwritten).
-- - A 492 / 625 rerun deletes the FIX/SAV/INC-01 rows here and restores the flat 492 rows (salary rows are regenerated from
--   financial_profile.monthly_income, i.e. 3,012,400 while this file's profile update stands) -> run this file again.
-- - A 636 / 655 rerun changes the card totals -> run this file again so the statements follow the ledger.
-- - A 491 rerun resets financial_profile.monthly_income (5,700,000) / fixed_expense / emergency_fund_target -> run this file again
--   (and 492 / 625 if they ran in between, otherwise the INC-00 rows keep 5,700,000 until this file overrides them).
-- - Scratch tables are regular tables (tmp_dodam_679_*), dropped in the cleanup section (MySQL TEMPORARY tables cannot be
--   referenced twice in one statement, ERROR 1137).
-- - Permanent writes happen inside one transaction; DDL for scratch tables is outside it. Never run with `mysql --force`.
--
-- Preflight (one row when every check passes; otherwise nothing is written)
-- --------------------------------------------------------------------------
--   context 1 (U0003, SEED 주거래 입출금 ACTIVE), card 1 (withdrawal_day 15), categories 7, savings accounts 2 (678 names of the
--   paying accounts, ACTIVE, monthly_amount = plan), 678 PAID instalments on the 15th Mar..Sep = 14 (the matured 목돈적금 has
--   no instalment in the window), loan 1 (633) with PAID 410,331 on the 10th Mar..Sep = 7, 492/625 salary months = 7,
--   plan rows 10 x 7 = 70.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 0. context
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_679_context;
CREATE TABLE tmp_dodam_679_context (
  user_id BIGINT NOT NULL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  primary_account_id BIGINT NOT NULL,
  primary_card_id BIGINT NULL,
  card_withdrawal_day INT NULL,
  loan_id BIGINT NULL
);
INSERT INTO tmp_dodam_679_context (user_id, email, primary_account_id, primary_card_id, card_withdrawal_day, loan_id)
SELECT
  u.user_id,
  u.email,
  a.account_id,
  c.card_id,
  c.withdrawal_day,
  (SELECT MIN(l.loan_id) FROM `loan` l WHERE l.user_id = u.user_id AND l.status = 'ACTIVE' AND l.repayment_day = 10 AND l.interest_rate = 8.5000)
FROM `user` u
JOIN `account` a
  ON a.user_id = u.user_id
 AND a.account_type = 'DEMAND_DEPOSIT'
 AND a.status = 'ACTIVE'
 AND a.source_type = 'SEED'
 AND a.account_name = '주거래 입출금'
LEFT JOIN `card` c
  ON c.user_id = u.user_id
 AND c.withdrawal_account_id = a.account_id
 AND c.card_id = (SELECT MIN(c2.card_id) FROM `card` c2 WHERE c2.user_id = u.user_id AND c2.withdrawal_account_id = a.account_id)
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND u.deleted_at IS NULL;

-- months: month_no 1..7 = 2026-03 .. 2026-09
DROP TABLE IF EXISTS tmp_dodam_679_months;
CREATE TABLE tmp_dodam_679_months (
  month_no TINYINT NOT NULL PRIMARY KEY,
  ym CHAR(6) NOT NULL,
  month_start DATE NOT NULL,
  id_prefix VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL
);
INSERT INTO tmp_dodam_679_months VALUES
  (1, '202603', '2026-03-01', 'SEED-U0003-',     '2026-09-22 09:00:00'),
  (2, '202604', '2026-04-01', 'SEED-U0003-',     '2026-09-22 09:00:00'),
  (3, '202605', '2026-05-01', 'SEED-U0003-',     '2026-09-22 09:00:00'),
  (4, '202606', '2026-06-01', 'SEED-U0003-',     '2026-09-22 09:00:00'),
  (5, '202607', '2026-07-01', 'SEED-U0003-',     '2026-09-22 09:00:00'),
  (6, '202608', '2026-08-01', 'SEED-U0003-',     '2026-09-22 09:00:00'),
  (7, '202609', '2026-09-01', 'SEED-SEP-U0003-', '2026-09-22 09:00:00');

DROP TABLE IF EXISTS tmp_dodam_679_categories;
CREATE TABLE tmp_dodam_679_categories (
  code VARCHAR(50) NOT NULL PRIMARY KEY,
  category_id BIGINT NOT NULL
);
INSERT INTO tmp_dodam_679_categories (code, category_id)
SELECT code, category_id FROM transaction_category
WHERE code IN ('HOUSING', 'UTILITIES', 'INSURANCE', 'LOAN_REPAYMENT', 'SAVING', 'ETC', 'INCOME') AND active = 1;

-- fixed items: one row per item, amounts per month (month_no 1..7). Every day is <= 27 so September fits the 625 window.
DROP TABLE IF EXISTS tmp_dodam_679_fixed_items;
CREATE TABLE tmp_dodam_679_fixed_items (
  kind CHAR(2) NOT NULL PRIMARY KEY,
  category_code VARCHAR(50) NOT NULL,
  day_of_month TINYINT NOT NULL,
  tx_time TIME NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  summary VARCHAR(255) NOT NULL,
  m1 BIGINT NOT NULL, m2 BIGINT NOT NULL, m3 BIGINT NOT NULL, m4 BIGINT NOT NULL,
  m5 BIGINT NOT NULL, m6 BIGINT NOT NULL, m7 BIGINT NOT NULL
);
INSERT INTO tmp_dodam_679_fixed_items VALUES
  ('01', 'HOUSING',        1,  '07:12:00', '한빛오피스텔 임대료',     '월세 자동이체',            500000, 500000, 500000, 500000, 500000, 500000, 500000),
  ('02', 'HOUSING',        5,  '08:03:00', '한빛오피스텔 관리사무소', '관리비',                   178000, 165000, 158000, 162000, 171000, 176000, 168000),
  ('03', 'HOUSING',        23, '06:41:00', '코웨이',                  '정수기 렌탈료',             32900,  32900,  32900,  32900,  32900,  32900,  32900),
  ('04', 'UTILITIES',      25, '06:22:00', 'SK텔레콤',                '통신요금(단말 할부 포함)',  89000,  89000,  89000,  89000,  89000,  89000,  89000),
  ('05', 'UTILITIES',      25, '06:23:00', 'SK브로드밴드',            '인터넷·TV 요금',            35200,  35200,  35200,  35200,  35200,  35200,  35200),
  ('06', 'UTILITIES',      22, '06:15:00', '한국전력공사',            '전기요금 자동납부',         31000,  27000,  29000,  36000,  48000,  55000,  41000),
  ('07', 'UTILITIES',      26, '06:18:00', '서울도시가스',            '도시가스 자동납부',         52000,  34000,  21000,  14000,  12000,  12000,  13000),
  ('08', 'INSURANCE',      20, '06:31:00', '삼성화재해상보험',        '실손보험료',                38400,  38400,  38400,  38400,  38400,  38400,  38400),
  ('09', 'INSURANCE',      20, '06:32:00', '한화생명',                '보험료 자동이체',          112000, 112000, 112000, 112000, 112000, 112000, 112000),
  ('10', 'LOAN_REPAYMENT', 10, '06:05:00', 'KB국민은행',              'KB 국민ONE대출 원리금',    410331, 410331, 410331, 410331, 410331, 410331, 410331);

DROP TABLE IF EXISTS tmp_dodam_679_fixed_plan;
CREATE TABLE tmp_dodam_679_fixed_plan (
  month_no TINYINT NOT NULL,
  kind CHAR(2) NOT NULL,
  category_code VARCHAR(50) NOT NULL,
  day_of_month TINYINT NOT NULL,
  tx_time TIME NOT NULL,
  amount BIGINT NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  summary VARCHAR(255) NOT NULL,
  PRIMARY KEY (month_no, kind),
  CHECK (amount > 0),
  CHECK (day_of_month BETWEEN 1 AND 27)
);
INSERT INTO tmp_dodam_679_fixed_plan
SELECT m.month_no, i.kind, i.category_code, i.day_of_month, i.tx_time,
       CASE m.month_no WHEN 1 THEN i.m1 WHEN 2 THEN i.m2 WHEN 3 THEN i.m3 WHEN 4 THEN i.m4 WHEN 5 THEN i.m5 WHEN 6 THEN i.m6 ELSE i.m7 END,
       i.merchant_name, i.summary
FROM tmp_dodam_679_months m
CROSS JOIN tmp_dodam_679_fixed_items i;

-- savings transfers: must equal the 678 savings_payment rows (15th, PAID Mar..Sep)
DROP TABLE IF EXISTS tmp_dodam_679_saving_items;
CREATE TABLE tmp_dodam_679_saving_items (
  kind CHAR(2) NOT NULL PRIMARY KEY,
  account_name VARCHAR(150) NOT NULL,
  amount BIGINT NOT NULL,
  tx_time TIME NOT NULL,
  summary VARCHAR(255) NOT NULL
);
INSERT INTO tmp_dodam_679_saving_items VALUES
  ('01', '신한 청년도약계좌',  300000, '09:35:00', '적금 자동이체'),
  ('02', '주택청약종합저축',   100000, '09:36:00', '청약 자동이체');

-- card charges per usage month (read-only input for the billing statements; built outside the transaction)
DROP TABLE IF EXISTS tmp_dodam_679_card_usage;
CREATE TABLE tmp_dodam_679_card_usage (
  billing_month CHAR(7) NOT NULL PRIMARY KEY,
  billing_date DATE NOT NULL,
  total_balance BIGINT NOT NULL
);
INSERT INTO tmp_dodam_679_card_usage (billing_month, billing_date, total_balance)
SELECT
  DATE_FORMAT(DATE_ADD(m.month_start, INTERVAL 1 MONTH), '%Y-%m'),
  DATE_ADD(m.month_start, INTERVAL 1 MONTH) + INTERVAL 14 DAY,
  COALESCE(SUM(tx.amount), 0)
FROM tmp_dodam_679_months m
CROSS JOIN tmp_dodam_679_context ctx
LEFT JOIN `transaction` tx
  ON tx.user_id = ctx.user_id AND tx.card_id = ctx.primary_card_id
 AND tx.transaction_type = 'EXPENSE' AND tx.payment_method = 'CARD'
 AND tx.transaction_date >= m.month_start AND tx.transaction_date < DATE_ADD(m.month_start, INTERVAL 1 MONTH)
GROUP BY m.month_no, m.month_start;

-- ---------------------------------------------------------------------------
-- 1. preflight (one row when every check passes)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_679_preflight;
CREATE TABLE tmp_dodam_679_preflight (
  ready TINYINT NOT NULL PRIMARY KEY,
  context_count INT NOT NULL,
  card_count INT NOT NULL,
  category_count INT NOT NULL,
  savings_account_count INT NOT NULL,
  savings_paid_count INT NOT NULL,
  loan_count INT NOT NULL,
  loan_paid_count INT NOT NULL,
  salary_month_count INT NOT NULL,
  fixed_plan_count INT NOT NULL
);
INSERT INTO tmp_dodam_679_preflight
SELECT 1, m.context_count, m.card_count, m.category_count, m.savings_account_count, m.savings_paid_count,
       m.loan_count, m.loan_paid_count, m.salary_month_count, m.fixed_plan_count
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_679_context) AS context_count,
    (SELECT COUNT(*) FROM tmp_dodam_679_context WHERE primary_card_id IS NOT NULL AND card_withdrawal_day = 15) AS card_count,
    (SELECT COUNT(*) FROM tmp_dodam_679_categories) AS category_count,
    -- the two paying 678 accounts, ACTIVE, with the plan's monthly amount (the matured 목돈적금 is not in the plan)
    (SELECT COUNT(*)
       FROM `account` a
       JOIN tmp_dodam_679_context ctx ON ctx.user_id = a.user_id
       JOIN deposit_saving ds ON ds.account_id = a.account_id
       JOIN tmp_dodam_679_saving_items si ON si.account_name = a.account_name AND si.amount = ds.monthly_amount
      WHERE a.account_type = 'SAVINGS' AND a.status = 'ACTIVE' AND a.source_type = 'SEED') AS savings_account_count,
    -- one PAID instalment on the 15th of each month Mar..Sep per paying account (2 x 7)
    (SELECT COUNT(DISTINCT a.account_id, sp.payment_date)
       FROM savings_payment sp
       JOIN `account` a ON a.account_id = sp.deposit_saving_id
       JOIN tmp_dodam_679_context ctx ON ctx.user_id = a.user_id
       JOIN tmp_dodam_679_saving_items si ON si.account_name = a.account_name AND si.amount = sp.amount
      WHERE a.account_type = 'SAVINGS' AND sp.status = 'PAID'
        AND sp.payment_date BETWEEN '2026-03-01' AND '2026-09-30' AND DAY(sp.payment_date) = 15) AS savings_paid_count,
    (SELECT COUNT(*) FROM tmp_dodam_679_context WHERE loan_id IS NOT NULL) AS loan_count,
    -- 633: PAID 410,331 on the 10th Mar..Sep
    (SELECT COUNT(DISTINCT r.repayment_date)
       FROM loan_repayment r
       JOIN tmp_dodam_679_context ctx ON ctx.loan_id = r.loan_id
      WHERE r.status = 'PAID' AND r.amount = 410331
        AND r.repayment_date BETWEEN '2026-03-01' AND '2026-09-30' AND DAY(r.repayment_date) = 10) AS loan_paid_count,
    -- 492 (6) + 625 (1) salary rows
    (SELECT COUNT(DISTINCT DATE_FORMAT(tx.transaction_date, '%Y%m'))
       FROM `transaction` tx
       JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED' AND tx.transaction_type = 'INCOME'
        AND (tx.transaction_id LIKE 'SEED-U0003-%-INC-00' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-INC-00')
        AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30') AS salary_month_count,
    (SELECT COUNT(*) FROM tmp_dodam_679_fixed_plan p
      WHERE EXISTS (SELECT 1 FROM tmp_dodam_679_categories c WHERE c.code = p.category_code)) AS fixed_plan_count
) m
WHERE m.context_count = 1 AND m.card_count = 1 AND m.category_count = 7 AND m.savings_account_count = 2
  AND m.savings_paid_count = 14 AND m.loan_count = 1 AND m.loan_paid_count = 7 AND m.salary_month_count = 7
  AND m.fixed_plan_count = 70
  -- 유효 날짜 수만 세면 같은 날 다른 회차나 잘못된 금액의 추가 납입을 놓친다.
  -- 기간 내 PAID 전체 건수도 맞아야 날짜·금액이 일치하는 회차만 정확히 남는다.
  AND (SELECT COUNT(*) FROM savings_payment sp
       JOIN `account` a ON a.account_id = sp.deposit_saving_id
       JOIN tmp_dodam_679_context ctx ON ctx.user_id = a.user_id
       JOIN tmp_dodam_679_saving_items si ON si.account_name = a.account_name
       WHERE a.account_type = 'SAVINGS' AND sp.status = 'PAID'
         AND sp.payment_date BETWEEN '2026-03-01' AND '2026-09-30') = 14
  AND (SELECT COUNT(*) FROM loan_repayment r
       JOIN tmp_dodam_679_context ctx ON ctx.loan_id = r.loan_id
       WHERE r.status = 'PAID'
         AND r.repayment_date BETWEEN '2026-03-01' AND '2026-09-30') = 7
  AND (SELECT COUNT(*) FROM financial_profile fp
       JOIN tmp_dodam_679_context ctx ON ctx.user_id = fp.user_id) = 1
  AND (SELECT COUNT(*) FROM tmp_dodam_679_card_usage WHERE total_balance > 0) = 7;

SELECT * FROM tmp_dodam_679_preflight;
SELECT 'DODAM_679_READY' AS check_name, COUNT(*) AS passed FROM tmp_dodam_679_preflight;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 2. remove U0003's previous fixed / saving / refund rows (492 flat rows, 625 September rows, previous runs).
--    TRF rows exist only in a DB loaded with the pre-revision file (allowance); they are removed and not recreated.
-- ---------------------------------------------------------------------------
DELETE tx FROM `transaction` tx
JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30'
  AND (tx.transaction_id LIKE 'SEED-U0003-%-FIX-%' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-FIX-%'
    OR tx.transaction_id LIKE 'SEED-U0003-%-SAV-%' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-SAV-%'
    OR tx.transaction_id LIKE 'SEED-U0003-%-TRF-%' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-TRF-%'
    OR tx.transaction_id LIKE 'SEED-U0003-%-INC-01')
  AND EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 3. fixed expenses (10 items x 7 months)
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT(m.id_prefix, m.ym, '-FIX-', p.kind),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL p.day_of_month - 1 DAY), p.tx_time, 'EXPENSE', p.amount, NULL,
  p.merchant_name, 'ACCOUNT', 'FIXED', p.summary, NULL,
  0, 'SEED', m.created_at, NULL
FROM tmp_dodam_679_fixed_plan p
JOIN tmp_dodam_679_months m ON m.month_no = p.month_no
JOIN tmp_dodam_679_categories cat ON cat.code = p.category_code
CROSS JOIN tmp_dodam_679_context ctx
WHERE EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 4. savings transfers (2 x 7, 15th) — mirror of the 678 instalments; the savings balances already hold the money
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT(m.id_prefix, m.ym, '-SAV-', s.kind),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL 14 DAY), s.tx_time, 'EXPENSE', s.amount, NULL,
  s.account_name, 'ACCOUNT', 'NON_SPENDING', s.summary, NULL,
  0, 'SEED', m.created_at, NULL
FROM tmp_dodam_679_saving_items s
CROSS JOIN tmp_dodam_679_months m
JOIN tmp_dodam_679_categories cat ON cat.code = 'SAVING'
CROSS JOIN tmp_dodam_679_context ctx
WHERE EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 5. salary override (7 INC-00 rows keep id / date / time, amount -> 3,012,400) and the March tax refund (INCOME)
-- ---------------------------------------------------------------------------
UPDATE `transaction` tx
JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
SET tx.amount = 3012400,
    tx.updated_at = '2026-09-22 09:00:00'
WHERE tx.source_type = 'SEED' AND tx.transaction_type = 'INCOME'
  AND (tx.transaction_id LIKE 'SEED-U0003-%-INC-00' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-INC-00')
  AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30'
  AND EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT(m.id_prefix, m.ym, '-INC-01'),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  '2026-03-20', '09:31:00', 'INCOME', 412350, NULL,
  '도담컴퍼니', 'ACCOUNT', 'NON_SPENDING', '연말정산 환급금', NULL,
  0, 'SEED', m.created_at, NULL
FROM tmp_dodam_679_months m
JOIN tmp_dodam_679_categories cat ON cat.code = 'INCOME'
CROSS JOIN tmp_dodam_679_context ctx
WHERE m.month_no = 1
  AND EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 6. card billing statements from the ledger's own card charges (usage month M -> billed on the 15th of M+1)
-- ---------------------------------------------------------------------------
DELETE s FROM card_billing_statement s
JOIN tmp_dodam_679_context ctx ON ctx.primary_card_id = s.card_id
JOIN tmp_dodam_679_card_usage u ON u.billing_month = s.billing_month
WHERE EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

INSERT INTO card_billing_statement (card_id, billing_month, billing_date, total_balance, status, payment_date)
SELECT
  ctx.primary_card_id, u.billing_month, u.billing_date, u.total_balance,
  CASE WHEN u.billing_date <= '2026-09-27' THEN 'PAID' ELSE 'SCHEDULED' END,
  CASE WHEN u.billing_date <= '2026-09-27' THEN u.billing_date ELSE NULL END
FROM tmp_dodam_679_card_usage u
CROSS JOIN tmp_dodam_679_context ctx
WHERE u.total_balance > 0
  AND EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 7. financial_profile: monthly_income = the salary written above (annual = x12; 491 keeps annual = monthly x 12).
--    fixed_expense = monthly fixed items excluding the loan (1,050,500 avg) + subscriptions (104,390),
--    rounded to 10,000 -> 1,150,000. Loan excluded because InstallmentAvailableFundsReader subtracts loan repayments separately.
--    emergency_fund_target keeps the 491 rule (fixed_expense x 4 for U0003).
-- ---------------------------------------------------------------------------
UPDATE financial_profile fp
JOIN tmp_dodam_679_context ctx ON ctx.user_id = fp.user_id
SET fp.monthly_income = 3012400,
    fp.annual_income = 36148800,
    fp.fixed_expense = 1150000,
    fp.emergency_fund_target = 4600000,
    fp.updated_at = '2026-09-22 09:00:00'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 8. account.last_transaction_date
-- ---------------------------------------------------------------------------
UPDATE `account` a
JOIN tmp_dodam_679_context ctx ON ctx.primary_account_id = a.account_id
JOIN (
  SELECT tx.account_id, MAX(tx.transaction_date) AS last_date
  FROM `transaction` tx
  JOIN tmp_dodam_679_context c ON c.primary_account_id = tx.account_id
  WHERE tx.source_type = 'SEED'
  GROUP BY tx.account_id
) latest ON latest.account_id = a.account_id
SET a.last_transaction_date = latest.last_date,
    a.updated_at = '2026-09-22 09:00:00'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_679_preflight pf WHERE pf.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 9. verification
-- ---------------------------------------------------------------------------
-- 9a. rows per month and kind. Expected per month: FIX 10, SAV 2 (400,000), INC 1 (3,012,400 salary); INC-01 only in 2026-03; no TRF.
SELECT DATE_FORMAT(tx.transaction_date, '%Y-%m') AS ym,
       SUBSTRING_INDEX(SUBSTRING_INDEX(tx.transaction_id, '-', -2), '-', 1) AS kind,
       COUNT(*) AS row_count, SUM(tx.amount) AS total
FROM `transaction` tx
JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30'
GROUP BY ym, kind
ORDER BY ym, kind;

-- 9b. fixed totals vs plan (plan_gap must be 0 for every month)
SELECT p.month_no, SUM(p.amount) AS plan_total, COALESCE(actual.total, 0) AS actual_total, SUM(p.amount) - COALESCE(actual.total, 0) AS plan_gap
FROM tmp_dodam_679_fixed_plan p
JOIN tmp_dodam_679_months m ON m.month_no = p.month_no
LEFT JOIN (
  SELECT DATE_FORMAT(tx.transaction_date, '%Y%m') AS ym, SUM(tx.amount) AS total
  FROM `transaction` tx
  JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
  WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE '%-FIX-%'
  GROUP BY DATE_FORMAT(tx.transaction_date, '%Y%m')
) actual ON actual.ym = m.ym
GROUP BY p.month_no, actual.total
ORDER BY p.month_no;

-- 9c. 627 guard: August vs May..Jul average per spending category (fixed categories must stay below +20%)
SELECT c.code,
       SUM(CASE WHEN tx.transaction_date BETWEEN '2026-08-01' AND '2026-08-31' THEN tx.amount ELSE 0 END) AS aug,
       ROUND(SUM(CASE WHEN tx.transaction_date BETWEEN '2026-05-01' AND '2026-07-31' THEN tx.amount ELSE 0 END) / 3) AS avg_may_jul,
       ROUND((SUM(CASE WHEN tx.transaction_date BETWEEN '2026-08-01' AND '2026-08-31' THEN tx.amount ELSE 0 END)
              / NULLIF(SUM(CASE WHEN tx.transaction_date BETWEEN '2026-05-01' AND '2026-07-31' THEN tx.amount ELSE 0 END) / 3, 0) - 1) * 100, 1) AS increase_pct
FROM `transaction` tx
JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
JOIN transaction_category c ON c.category_id = tx.category_id
WHERE tx.transaction_type = 'EXPENSE' AND (tx.spending_type IS NULL OR tx.spending_type <> 'NON_SPENDING') AND tx.hidden = 0
  AND tx.transaction_date BETWEEN '2026-05-01' AND '2026-08-31'
GROUP BY c.code
ORDER BY c.code;

-- 9d. savings transfers vs 678 instalments, loan rows vs 633 repayments (both gaps must be 0)
SELECT
  (SELECT COALESCE(SUM(tx.amount), 0) FROM `transaction` tx JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
    WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE '%-SAV-%' AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30')
  - (SELECT COALESCE(SUM(sp.amount), 0) FROM savings_payment sp JOIN `account` a ON a.account_id = sp.deposit_saving_id
      JOIN tmp_dodam_679_context ctx ON ctx.user_id = a.user_id
    WHERE a.account_type = 'SAVINGS' AND sp.status = 'PAID' AND sp.payment_date BETWEEN '2026-03-01' AND '2026-09-30') AS saving_gap,
  (SELECT COALESCE(SUM(tx.amount), 0) FROM `transaction` tx JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
    WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE '%-FIX-10' AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30')
  - (SELECT COALESCE(SUM(r.amount), 0) FROM loan_repayment r JOIN tmp_dodam_679_context ctx ON ctx.loan_id = r.loan_id
    WHERE r.status = 'PAID' AND r.repayment_date BETWEEN '2026-03-01' AND '2026-09-30') AS loan_gap;

-- 9e. card billing statements: total must equal the previous month's card charges (bill_gap 0)
SELECT s.billing_month, s.billing_date, s.status, s.payment_date, s.total_balance, u.total_balance AS ledger_card_total,
       s.total_balance - u.total_balance AS bill_gap
FROM card_billing_statement s
JOIN tmp_dodam_679_context ctx ON ctx.primary_card_id = s.card_id
JOIN tmp_dodam_679_card_usage u ON u.billing_month = s.billing_month
ORDER BY s.billing_month;

-- 9f. profile and account snapshot
SELECT fp.monthly_income, fp.fixed_expense, fp.emergency_fund_target, fp.payday, a.balance, a.last_transaction_date
FROM financial_profile fp
JOIN tmp_dodam_679_context ctx ON ctx.user_id = fp.user_id
JOIN `account` a ON a.account_id = ctx.primary_account_id;

-- 9g. contract violations (must be 0): wrong account/card, future rows beyond the 625 window, non-positive amounts
SELECT COUNT(*) AS violation_count
FROM `transaction` tx
JOIN tmp_dodam_679_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND (tx.transaction_id LIKE '%-FIX-%' OR tx.transaction_id LIKE '%-SAV-%' OR tx.transaction_id LIKE '%-TRF-%' OR tx.transaction_id LIKE '%-INC-01')
  AND (tx.account_id <> ctx.primary_account_id OR tx.card_id IS NOT NULL OR tx.amount <= 0
       OR tx.transaction_date > '2026-09-27' OR tx.transaction_date < '2026-03-01');

-- ---------------------------------------------------------------------------
-- 10. cleanup
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_679_card_usage;
DROP TABLE IF EXISTS tmp_dodam_679_preflight;
DROP TABLE IF EXISTS tmp_dodam_679_saving_items;
DROP TABLE IF EXISTS tmp_dodam_679_fixed_plan;
DROP TABLE IF EXISTS tmp_dodam_679_fixed_items;
DROP TABLE IF EXISTS tmp_dodam_679_categories;
DROP TABLE IF EXISTS tmp_dodam_679_months;
DROP TABLE IF EXISTS tmp_dodam_679_context;
