-- DODAM Transaction (spending / income / saving transfer) seed
-- Jira: S15P21D105-492
-- Dialect: MySQL 8.x
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07),
--               backend/src/main/resources/db/migration/V6__create_transaction_category_and_transaction.sql
-- Dependency: S15P21D105-491 User/Account seed, S15P21D105-522 Card seed,
--             V6 transaction_category reference rows (20 codes)
-- Dataset: 4,000 K-Means users x 6 months (2026-03-01 ~ 2026-08-31),
--          about 84 transactions per user (~336,000 rows), demo users U0001~U0004 get 16 variable slots per month
-- Snapshot: 2026-09-07
--
-- What this file does
-- -------------------
-- 1. Deletes the previous run's seed transactions (transaction_id LIKE 'SEED-U%' and source_type = 'SEED')
--    for the 4,000 K-Means users, then regenerates them deterministically. Rerunning is safe.
-- 2. Generates, per user per month:
--      INCOME  salary on payday                                 -> category INCOME, spending_type NON_SPENDING, payment ACCOUNT
--      FIXED   housing (1st), utilities (5th), insurance (20th) -> categories HOUSING / UTILITIES / INSURANCE, spending_type FIXED
--      SUBS    subscription charges (12th, even users also 25th) -> category SUBSCRIPTION, FIXED, payment CARD
--              (amounts match DODAM_SUBSCRIPTION_SEED.sql so the two files agree)
--      SAVING  transfer to savings on payday + 1                -> category SAVING, NON_SPENDING, payment ACCOUNT
--      VARIABLE 8 slots (demo users 16)                         -> FOOD/CAFE/GROCERY/PUBLIC_TRANSPORT/TAXI/SHOPPING/BEAUTY/CULTURE/MEDICAL/ETC, VARIABLE
-- 3. Updates account.last_transaction_date of the primary demand-deposit account from the generated rows
--    (the 491 file leaves it NULL on purpose and delegates the first update to this task).
--
-- K-Means fixture contract (matches 491)
-- ---------------------------------------
-- The behavior code cycles by sequence number and is never persisted:
--   MOD(seq-1, 4): 0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE
-- Monthly shares of monthly_income (financial_profile is the source of truth for income / fixed_expense / payday):
--   BALANCED          variable 30%, saving transfer 15%
--   SPENDING_FOCUSED  variable 55%, saving transfer  3%
--   SAVING_FOCUSED    variable 20%, saving transfer 30%
--   VOLATILE          variable 70% on odd months / 20% on even months, saving transfer 8%
-- All amounts are deterministic functions of (sequence_no, month_no, slot). No RAND().
--
-- Conventions
-- -----------
-- - "Spending" as consumed by the Spending API (506): transaction_type = EXPENSE and spending_type <> NON_SPENDING.
--   SAVING transfers are EXPENSE + NON_SPENDING so they leave the demand-deposit balance but are not counted as spending.
-- - balance_after stays NULL: the seed does not replay balances (491 sets a static balance snapshot).
-- - card_id: variable CARD payments and subscription charges use the user's primary card
--   (MIN(card_id) among the cards drawn on the primary demand-deposit account). ACCOUNT payments carry account_id.
-- - U0002_INACTIVE (the dormant demonstration account) receives no transactions and keeps last_transaction_date NULL.
-- - transaction_id format: SEED-U0001-202603-VAR-03 (<= 100 chars, unique).
--
-- Downstream: 493 Goal seed reads account balances only; Spending/Goal APIs read these rows.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 0. helpers
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_digit;
CREATE TEMPORARY TABLE tmp_dodam_492_digit (
  digit TINYINT NOT NULL,
  PRIMARY KEY (digit),
  CHECK (digit BETWEEN 0 AND 9)
);
INSERT INTO tmp_dodam_492_digit (digit) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

-- six-month window
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_months;
CREATE TEMPORARY TABLE tmp_dodam_492_months (
  month_no TINYINT NOT NULL,
  month_start DATE NOT NULL,
  month_end DATE NOT NULL,
  ym CHAR(6) NOT NULL,
  PRIMARY KEY (month_no)
);
INSERT INTO tmp_dodam_492_months (month_no, month_start, month_end, ym)
SELECT
  d.digit + 1,
  DATE_ADD('2026-03-01', INTERVAL d.digit MONTH),
  LAST_DAY(DATE_ADD('2026-03-01', INTERVAL d.digit MONTH)),
  DATE_FORMAT(DATE_ADD('2026-03-01', INTERVAL d.digit MONTH), '%Y%m')
FROM tmp_dodam_492_digit d
WHERE d.digit BETWEEN 0 AND 5;

-- variable spending slots 0..15
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_slots;
CREATE TEMPORARY TABLE tmp_dodam_492_slots (
  slot_no TINYINT NOT NULL,
  PRIMARY KEY (slot_no)
);
INSERT INTO tmp_dodam_492_slots (slot_no)
SELECT tens.digit * 10 + ones.digit
FROM tmp_dodam_492_digit tens
CROSS JOIN tmp_dodam_492_digit ones
WHERE tens.digit * 10 + ones.digit BETWEEN 0 AND 15;

-- variable categories (index -> code, merchant). Weighted by repeating FOOD.
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_variable_categories;
CREATE TEMPORARY TABLE tmp_dodam_492_variable_categories (
  pick_index TINYINT NOT NULL,
  category_code VARCHAR(50) NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  PRIMARY KEY (pick_index)
);
INSERT INTO tmp_dodam_492_variable_categories (pick_index, category_code, merchant_name) VALUES
  (0, 'FOOD', '도담 식당'),
  (1, 'FOOD', '도담 배달'),
  (2, 'FOOD', '도담 분식'),
  (3, 'FOOD', '도담 한식'),
  (4, 'CAFE', '도담 카페'),
  (5, 'CAFE', '도담 커피'),
  (6, 'GROCERY', '도담 마트'),
  (7, 'GROCERY', '도담 편의점'),
  (8, 'PUBLIC_TRANSPORT', '교통카드 충전'),
  (9, 'TAXI', '도담 택시'),
  (10, 'SHOPPING', '도담 몰'),
  (11, 'SHOPPING', '도담 패션'),
  (12, 'BEAUTY', '도담 뷰티'),
  (13, 'CULTURE', '도담 시네마'),
  (14, 'MEDICAL', '도담 의원'),
  (15, 'ETC', '기타 가맹점');

-- ---------------------------------------------------------------------------
-- 1. seed users (same formulas as 491) joined to their persisted rows
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_users;
CREATE TEMPORARY TABLE tmp_dodam_492_users (
  sequence_no INT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  email VARCHAR(255) NOT NULL,
  behavior_index TINYINT NOT NULL,
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_tmp_dodam_492_user_email (email),
  CHECK (behavior_index BETWEEN 0 AND 3)
);
INSERT INTO tmp_dodam_492_users (sequence_no, synthetic_user_code, email, behavior_index)
SELECT
  numbers.sequence_no,
  CONCAT('U', LPAD(numbers.sequence_no, 4, '0')),
  CONCAT('seed.kmeans.u', LPAD(numbers.sequence_no, 4, '0'), '@dodam.invalid'),
  MOD(numbers.sequence_no - 1, 4)
FROM (
  SELECT thousands.digit * 1000 + hundreds.digit * 100 + tens.digit * 10 + ones.digit + 1 AS sequence_no
  FROM tmp_dodam_492_digit thousands
  CROSS JOIN tmp_dodam_492_digit hundreds
  CROSS JOIN tmp_dodam_492_digit tens
  CROSS JOIN tmp_dodam_492_digit ones
) numbers
WHERE numbers.sequence_no BETWEEN 1 AND 4000;

-- Per-user generation context. Income / fixed expense / payday come from financial_profile (491 output).
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_context;
CREATE TEMPORARY TABLE tmp_dodam_492_context (
  sequence_no INT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  behavior_index TINYINT NOT NULL,
  user_id BIGINT NOT NULL,
  primary_account_id BIGINT NOT NULL,
  primary_card_id BIGINT NULL,
  monthly_income BIGINT NOT NULL,
  fixed_expense BIGINT NOT NULL,
  payday TINYINT NOT NULL,
  variable_ratio_odd TINYINT NOT NULL,
  variable_ratio_even TINYINT NOT NULL,
  saving_ratio TINYINT NOT NULL,
  variable_slots TINYINT NOT NULL,
  subscription_amount_1 BIGINT NOT NULL,
  subscription_amount_2 BIGINT NULL,
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_tmp_dodam_492_context_user (user_id)
);
INSERT INTO tmp_dodam_492_context (
  sequence_no, synthetic_user_code, behavior_index, user_id, primary_account_id, primary_card_id,
  monthly_income, fixed_expense, payday,
  variable_ratio_odd, variable_ratio_even, saving_ratio, variable_slots,
  subscription_amount_1, subscription_amount_2
)
SELECT
  seed.sequence_no,
  seed.synthetic_user_code,
  seed.behavior_index,
  user_row.user_id,
  account_row.account_id,
  (SELECT MIN(card_row.card_id)
     FROM `card` card_row
    WHERE card_row.user_id = user_row.user_id
      AND card_row.withdrawal_account_id = account_row.account_id),
  COALESCE(profile_row.monthly_income, 2300000),
  COALESCE(profile_row.fixed_expense, 700000),
  COALESCE(profile_row.payday, 10),
  CASE seed.behavior_index WHEN 0 THEN 30 WHEN 1 THEN 55 WHEN 2 THEN 20 ELSE 70 END,
  CASE seed.behavior_index WHEN 0 THEN 30 WHEN 1 THEN 55 WHEN 2 THEN 20 ELSE 20 END,
  CASE seed.behavior_index WHEN 0 THEN 15 WHEN 1 THEN 3 WHEN 2 THEN 30 ELSE 8 END,
  CASE WHEN seed.sequence_no <= 4 THEN 16 ELSE 8 END,
  9900 + MOD(seed.sequence_no, 3) * 2500,
  CASE WHEN MOD(seed.sequence_no, 2) = 0 THEN 4900 ELSE NULL END
FROM tmp_dodam_492_users seed
JOIN `user` user_row ON user_row.email = seed.email
JOIN `account` account_row
  ON account_row.user_id = user_row.user_id
 AND account_row.account_type = 'DEMAND_DEPOSIT'
 AND account_row.source_type = 'SEED'
 AND account_row.account_name = '주거래 입출금'
LEFT JOIN financial_profile profile_row ON profile_row.user_id = user_row.user_id;

-- category lookup (V6 reference rows)
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_categories;
CREATE TEMPORARY TABLE tmp_dodam_492_categories (
  code VARCHAR(50) NOT NULL,
  category_id BIGINT NOT NULL,
  PRIMARY KEY (code)
);
INSERT INTO tmp_dodam_492_categories (code, category_id)
SELECT code, category_id
FROM transaction_category
WHERE code IN ('FOOD', 'CAFE', 'GROCERY', 'PUBLIC_TRANSPORT', 'TAXI', 'SHOPPING', 'BEAUTY', 'CULTURE',
               'TRAVEL', 'MEDICAL', 'EDUCATION', 'HOUSING', 'UTILITIES', 'INSURANCE', 'SUBSCRIPTION',
               'LOAN_REPAYMENT', 'SAVING', 'TRANSFER', 'INCOME', 'ETC')
  AND active = 1;

-- ---------------------------------------------------------------------------
-- 2. preflight: every permanent write is gated by ready = 1
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_492_preflight;
CREATE TEMPORARY TABLE tmp_dodam_492_preflight (
  ready TINYINT(1) NOT NULL,
  context_user_count INT NOT NULL,
  users_with_card_count INT NOT NULL,
  category_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (context_user_count = 4000),
  CHECK (users_with_card_count = 4000),
  CHECK (category_count = 20)
);
INSERT INTO tmp_dodam_492_preflight (ready, context_user_count, users_with_card_count, category_count)
SELECT
  CASE WHEN checks.context_user_count = 4000
        AND checks.users_with_card_count = 4000
        AND checks.category_count = 20
       THEN 1 ELSE 0 END,
  checks.context_user_count,
  checks.users_with_card_count,
  checks.category_count
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_492_context) AS context_user_count,
    (SELECT COUNT(*) FROM tmp_dodam_492_context WHERE primary_card_id IS NOT NULL) AS users_with_card_count,
    (SELECT COUNT(*) FROM tmp_dodam_492_categories) AS category_count
) checks;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 3. remove the previous run for these users
-- ---------------------------------------------------------------------------
DELETE tx
FROM `transaction` tx
JOIN tmp_dodam_492_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND tx.transaction_id LIKE 'SEED-U%'
  AND EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4a. INCOME: salary on payday
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-', ctx.synthetic_user_code, '-', m.ym, '-INC-00'),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL ctx.payday - 1 DAY), '09:30:00', 'INCOME', ctx.monthly_income, NULL,
  '도담컴퍼니', 'ACCOUNT', 'NON_SPENDING', '급여', NULL,
  0, 'SEED', '2026-09-07 09:00:00', NULL
FROM tmp_dodam_492_context ctx
CROSS JOIN tmp_dodam_492_months m
JOIN tmp_dodam_492_categories cat ON cat.code = 'INCOME'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4b. FIXED: housing (65% of fixed_expense, 1st), utilities (20%, 5th, +-10%), insurance (15%, 20th)
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-', ctx.synthetic_user_code, '-', m.ym, '-FIX-', fixed.kind),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL fixed.day_of_month - 1 DAY), fixed.tx_time, 'EXPENSE',
  GREATEST(1000, ROUND(ctx.fixed_expense * fixed.share_percent / 100.0
                       * (CASE WHEN fixed.kind = '02' THEN (90 + MOD(ctx.sequence_no * 3 + m.month_no * 7, 21)) / 100.0 ELSE 1 END),
                 -2)),
  NULL,
  fixed.merchant_name, 'ACCOUNT', 'FIXED', fixed.summary, NULL,
  0, 'SEED', '2026-09-07 09:00:00', NULL
FROM tmp_dodam_492_context ctx
CROSS JOIN tmp_dodam_492_months m
CROSS JOIN (
  SELECT '01' AS kind, 'HOUSING' AS category_code, 1 AS day_of_month, 65 AS share_percent, '도담 주거 관리사무소' AS merchant_name, '월세·관리비' AS summary, '10:00:00' AS tx_time
  UNION ALL
  SELECT '02', 'UTILITIES', 5, 20, '도담 통신·공과금', '통신·공과금', '11:00:00'
  UNION ALL
  SELECT '03', 'INSURANCE', 20, 15, '도담 보험', '보험료', '10:30:00'
) fixed
JOIN tmp_dodam_492_categories cat ON cat.code = fixed.category_code
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4c. SUBSCRIPTION charges on the primary card (amounts shared with DODAM_SUBSCRIPTION_SEED.sql)
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-', ctx.synthetic_user_code, '-', m.ym, '-SUB-', subs.kind),
  ctx.user_id, NULL, ctx.primary_card_id, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL subs.day_of_month - 1 DAY), '00:10:00', 'EXPENSE', subs.amount, NULL,
  subs.merchant_name, 'CARD', 'FIXED', subs.summary, NULL,
  0, 'SEED', '2026-09-07 09:00:00', NULL
FROM tmp_dodam_492_context ctx
CROSS JOIN tmp_dodam_492_months m
JOIN (
  SELECT c.sequence_no, '01' AS kind, 12 AS day_of_month, c.subscription_amount_1 AS amount,
         CASE MOD(c.sequence_no, 3) WHEN 0 THEN '도담 뮤직' WHEN 1 THEN '도담 비디오' ELSE '도담 클라우드' END AS merchant_name,
         '구독 결제' AS summary
  FROM tmp_dodam_492_context c
  UNION ALL
  SELECT c.sequence_no, '02', 25, c.subscription_amount_2, '도담 리딩', '구독 결제'
  FROM tmp_dodam_492_context c
  WHERE c.subscription_amount_2 IS NOT NULL
) subs ON subs.sequence_no = ctx.sequence_no
JOIN tmp_dodam_492_categories cat ON cat.code = 'SUBSCRIPTION'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4d. SAVING transfer on payday + 1 (EXPENSE + NON_SPENDING: leaves the account, not counted as spending)
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-', ctx.synthetic_user_code, '-', m.ym, '-SAV-00'),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL LEAST(ctx.payday, 27) DAY), '09:35:00', 'EXPENSE',
  GREATEST(10000, ROUND(ctx.monthly_income * ctx.saving_ratio / 100.0, -3)), NULL,
  '도담테스트은행 저축계좌', 'ACCOUNT', 'NON_SPENDING', '적금·예금 이체', NULL,
  0, 'SEED', '2026-09-07 09:00:00', NULL
FROM tmp_dodam_492_context ctx
CROSS JOIN tmp_dodam_492_months m
JOIN tmp_dodam_492_categories cat ON cat.code = 'SAVING'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4e. VARIABLE spending slots. Deterministic hash: h = seq*7919 + month*104729 + slot*15485863
--     amount  = budget / slots * (0.6 .. 1.4)   rounded to 100 won, minimum 1,000
--     day     = 1 + MOD(h, 28)
--     category by MOD(h / 7, 16) over the weighted pick table
--     payment = CARD for 7 of 10 picks (primary card), otherwise ACCOUNT (primary account)
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-', ctx.synthetic_user_code, '-', m.ym, '-VAR-', LPAD(s.slot_no, 2, '0')),
  ctx.user_id,
  CASE WHEN MOD(gen.h, 10) < 7 THEN NULL ELSE ctx.primary_account_id END,
  CASE WHEN MOD(gen.h, 10) < 7 THEN ctx.primary_card_id ELSE NULL END,
  cat.category_id,
  DATE_ADD(m.month_start, INTERVAL MOD(gen.h, 28) DAY),
  MAKETIME(8 + MOD(gen.h DIV 13, 14), MOD(gen.h DIV 97, 60), 0),
  'EXPENSE',
  GREATEST(1000, ROUND(
      (ctx.monthly_income * (CASE WHEN MOD(m.month_no, 2) = 1 THEN ctx.variable_ratio_odd ELSE ctx.variable_ratio_even END) / 100.0)
      / ctx.variable_slots
      * ((60 + MOD(gen.h DIV 3, 81)) / 100.0),
      -2)),
  NULL,
  pick.merchant_name,
  CASE WHEN MOD(gen.h, 10) < 7 THEN 'CARD' ELSE 'ACCOUNT' END,
  'VARIABLE',
  pick.merchant_name,
  NULL,
  0, 'SEED', '2026-09-07 09:00:00', NULL
FROM tmp_dodam_492_context ctx
CROSS JOIN tmp_dodam_492_months m
JOIN tmp_dodam_492_slots s ON s.slot_no < ctx.variable_slots
JOIN (
  SELECT c.sequence_no, mm.month_no, ss.slot_no,
         (c.sequence_no * 7919 + mm.month_no * 104729 + ss.slot_no * 15485863) AS h
  FROM tmp_dodam_492_context c
  CROSS JOIN tmp_dodam_492_months mm
  CROSS JOIN tmp_dodam_492_slots ss
) gen ON gen.sequence_no = ctx.sequence_no AND gen.month_no = m.month_no AND gen.slot_no = s.slot_no
JOIN tmp_dodam_492_variable_categories pick ON pick.pick_index = MOD(gen.h DIV 7, 16)
JOIN tmp_dodam_492_categories cat ON cat.code = pick.category_code
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 5. account.last_transaction_date for the primary demand-deposit accounts (491 contract).
--    Only ACCOUNT-payment rows touch the account. The dormant U0002_INACTIVE account is untouched.
-- ---------------------------------------------------------------------------
UPDATE `account` account_row
JOIN (
  SELECT tx.account_id, MAX(tx.transaction_date) AS last_date
  FROM `transaction` tx
  JOIN tmp_dodam_492_context ctx ON ctx.primary_account_id = tx.account_id
  WHERE tx.source_type = 'SEED'
    AND tx.transaction_id LIKE 'SEED-U%'
  GROUP BY tx.account_id
) latest ON latest.account_id = account_row.account_id
SET account_row.last_transaction_date = latest.last_date,
    account_row.updated_at = '2026-09-07 09:00:00'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492_preflight preflight WHERE preflight.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 6. verification
-- ---------------------------------------------------------------------------
-- Expected: 4000 users, 24000 income, 72000 fixed, 36000 subscription (24000 + 12000), 24000 saving,
--           variable = 3996*6*8 + 4*6*16 = 192192; total 348192.
SELECT
  SUBSTRING_INDEX(SUBSTRING_INDEX(tx.transaction_id, '-', 4), '-', -1) AS kind,
  COUNT(*) AS row_count,
  MIN(tx.transaction_date) AS first_date,
  MAX(tx.transaction_date) AS last_date,
  SUM(tx.amount) AS total_amount
FROM `transaction` tx
JOIN tmp_dodam_492_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-U%'
GROUP BY kind
ORDER BY kind;

-- Expected: spending share of income per behavior cohort roughly 30/55/20/45 (variable) + fixed.
SELECT
  ctx.behavior_index,
  ROUND(SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type <> 'NON_SPENDING' THEN tx.amount ELSE 0 END)
        / SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END) * 100, 1) AS spending_ratio_percent,
  ROUND(SUM(CASE WHEN tx.spending_type = 'NON_SPENDING' AND tx.transaction_type = 'EXPENSE' THEN tx.amount ELSE 0 END)
        / SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END) * 100, 1) AS saving_ratio_percent
FROM `transaction` tx
JOIN tmp_dodam_492_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-U%'
GROUP BY ctx.behavior_index
ORDER BY ctx.behavior_index;

-- Expected: 0 (every row inside the window, positive amount, valid FK targets, exactly one of account_id / card_id).
SELECT COUNT(*) AS contract_violation_count
FROM `transaction` tx
JOIN tmp_dodam_492_context ctx ON ctx.user_id = tx.user_id
LEFT JOIN `account` a ON a.account_id = tx.account_id AND a.user_id = tx.user_id
LEFT JOIN `card` c ON c.card_id = tx.card_id AND c.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-U%'
  AND (tx.transaction_date NOT BETWEEN '2026-03-01' AND '2026-08-31'
       OR tx.amount <= 0
       OR tx.category_id IS NULL
       OR (tx.account_id IS NULL AND tx.card_id IS NULL)
       OR (tx.account_id IS NOT NULL AND tx.card_id IS NOT NULL)
       OR (tx.account_id IS NOT NULL AND a.account_id IS NULL)
       OR (tx.card_id IS NOT NULL AND c.card_id IS NULL)
       OR (tx.payment_method = 'CARD' AND tx.card_id IS NULL)
       OR (tx.payment_method = 'ACCOUNT' AND tx.account_id IS NULL));

-- Expected: 4000 primary accounts with last_transaction_date = 2026-08-xx, dormant account still NULL.
SELECT
  SUM(account_row.last_transaction_date IS NOT NULL) AS primary_accounts_with_last_date,
  SUM(account_row.last_transaction_date IS NULL) AS primary_accounts_without_last_date
FROM `account` account_row
JOIN tmp_dodam_492_context ctx ON ctx.primary_account_id = account_row.account_id;

SELECT account_row.account_name, account_row.last_transaction_date
FROM `account` account_row
JOIN `user` user_row ON user_row.user_id = account_row.user_id
WHERE user_row.email = 'seed.kmeans.u0002@dodam.invalid'
ORDER BY account_row.account_id;
