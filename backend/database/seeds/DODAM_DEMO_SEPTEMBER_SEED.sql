-- DODAM demo users (U0001~U0004) September 2026 transaction seed
-- Jira: S15P21D105-625
-- Dialect: MySQL 8.x
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07),
--               V6__create_transaction_category_and_transaction.sql, V30__create_subscription_and_payment_history.sql
-- Dependency: S15P21D105-491 User/Account seed, S15P21D105-522 Card seed,
--             DODAM_TRANSACTION_SEED.sql (492, six-month window), DODAM_SUBSCRIPTION_SEED.sql (subscriptions SUB-U000x-0y)
-- Dataset: 4 demo users x 1 partial month (2026-09-01 ~ 2026-09-27), 90 transactions + 6 subscription charges
-- Snapshot: 2026-09-16
--
-- Why this file exists
-- --------------------
-- The 492 seed stops at 2026-08-31, but the app opens on the current month. During the final demo
-- (2026-09-28) the home / ledger screens of the login-enabled demo users (U0001~U0004, see
-- DODAM_DEMO_PASSWORD_SEED.sql) would show an empty September. This file extends only those four users
-- through 2026-09-27 (the day before the demo) so no seeded transaction is dated after the demo day.
-- The other 3,996 K-Means users keep the six-month window untouched.
--
-- What this file does
-- -------------------
-- 1. Deletes the previous run (transaction_id LIKE 'SEED-SEP-U%' and source_type = 'SEED') and regenerates.
-- 2. Generates September rows with the same deterministic rules as DODAM_TRANSACTION_SEED.sql, month_no = 7
--    (September is the month after the six-month window: 3월=1 ... 8월=6, 9월=7), with two differences:
--      - variable-spending day = 1 + MOD(h, 27) instead of 1 + MOD(h, 28), so every row is on or before the 27th
--      - the monthly variable budget is NOT prorated to 27 days: the demo screens should look like a full month
--    Salary (payday 10/15/20/25), fixed costs (1st/5th/20th), subscriptions (12th, 25th) and the saving transfer
--    (payday + 1, at most the 26th) all fall inside the window for these four users.
-- 3. Adds the September subscription_payment_history rows (SUCCESS, same date and amount as the SUB transactions)
--    and moves next_payment_date of the four users' ACTIVE subscriptions to October, so "정기결제 요약" still shows an
--    upcoming charge on the demo day instead of an already-passed September date.
-- 4. Updates account.last_transaction_date of the four primary demand-deposit accounts.
--
-- Rerun / ordering contract
-- -------------------------
-- - transaction_id format: SEED-SEP-U0001-202609-VAR-03. The prefix differs from 492 ('SEED-U%') on purpose:
--   a 492 rerun neither deletes these rows nor reports them as window violations in its verification.
-- - Run this file AFTER 492 and DODAM_SUBSCRIPTION_SEED.sql, and run it again whenever either of them is rerun:
--   492 resets last_transaction_date to August and the subscription seed resets next_payment_date to September
--   and deletes September history rows.
-- - Rerunning this file alone is idempotent.
--
-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references the same TEMPORARY table more
-- than once. Scratch tables are regular tables (tmp_dodam_625_*) and are dropped at the end.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 0. helpers (same shapes as 492)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_625_digit;
CREATE TABLE tmp_dodam_625_digit (
  digit TINYINT NOT NULL,
  PRIMARY KEY (digit),
  CHECK (digit BETWEEN 0 AND 9)
);
INSERT INTO tmp_dodam_625_digit (digit) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

-- single partial month: 2026-09-01 ~ 2026-09-27, month_no 7 continues the 492 hash sequence
DROP TABLE IF EXISTS tmp_dodam_625_months;
CREATE TABLE tmp_dodam_625_months (
  month_no TINYINT NOT NULL,
  month_start DATE NOT NULL,
  month_end DATE NOT NULL,
  ym CHAR(6) NOT NULL,
  PRIMARY KEY (month_no)
);
INSERT INTO tmp_dodam_625_months (month_no, month_start, month_end, ym)
VALUES (7, '2026-09-01', '2026-09-27', '202609');

-- variable spending slots 0..15 (demo users have 16 slots)
DROP TABLE IF EXISTS tmp_dodam_625_slots;
CREATE TABLE tmp_dodam_625_slots (
  slot_no TINYINT NOT NULL,
  PRIMARY KEY (slot_no)
);
INSERT INTO tmp_dodam_625_slots (slot_no)
SELECT tens.digit * 10 + ones.digit
FROM tmp_dodam_625_digit tens
CROSS JOIN tmp_dodam_625_digit ones
WHERE tens.digit * 10 + ones.digit BETWEEN 0 AND 15;

-- variable categories (identical to 492 so September looks like the earlier months)
DROP TABLE IF EXISTS tmp_dodam_625_variable_categories;
CREATE TABLE tmp_dodam_625_variable_categories (
  pick_index TINYINT NOT NULL,
  category_code VARCHAR(50) NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  PRIMARY KEY (pick_index)
);
INSERT INTO tmp_dodam_625_variable_categories (pick_index, category_code, merchant_name) VALUES
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
-- 1. demo users U0001~U0004 joined to their persisted rows (same context columns as 492)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_625_context;
CREATE TABLE tmp_dodam_625_context (
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
  UNIQUE KEY uk_tmp_dodam_625_context_user (user_id)
);
INSERT INTO tmp_dodam_625_context (
  sequence_no, synthetic_user_code, behavior_index, user_id, primary_account_id, primary_card_id,
  monthly_income, fixed_expense, payday,
  variable_ratio_odd, variable_ratio_even, saving_ratio, variable_slots,
  subscription_amount_1, subscription_amount_2
)
SELECT
  seed.sequence_no,
  CONCAT('U', LPAD(seed.sequence_no, 4, '0')),
  MOD(seed.sequence_no - 1, 4),
  user_row.user_id,
  account_row.account_id,
  (SELECT MIN(card_row.card_id)
     FROM `card` card_row
    WHERE card_row.user_id = user_row.user_id
      AND card_row.withdrawal_account_id = account_row.account_id),
  COALESCE(profile_row.monthly_income, 2300000),
  COALESCE(profile_row.fixed_expense, 700000),
  COALESCE(profile_row.payday, 10),
  CASE MOD(seed.sequence_no - 1, 4) WHEN 0 THEN 30 WHEN 1 THEN 55 WHEN 2 THEN 20 ELSE 70 END,
  CASE MOD(seed.sequence_no - 1, 4) WHEN 0 THEN 30 WHEN 1 THEN 55 WHEN 2 THEN 20 ELSE 20 END,
  CASE MOD(seed.sequence_no - 1, 4) WHEN 0 THEN 15 WHEN 1 THEN 3 WHEN 2 THEN 30 ELSE 8 END,
  16,
  9900 + MOD(seed.sequence_no, 3) * 2500,
  CASE WHEN MOD(seed.sequence_no, 2) = 0 THEN 4900 ELSE NULL END
FROM (
  SELECT d.digit AS sequence_no FROM tmp_dodam_625_digit d WHERE d.digit BETWEEN 1 AND 4
) seed
JOIN `user` user_row ON user_row.email = CONCAT('seed.kmeans.u', LPAD(seed.sequence_no, 4, '0'), '@dodam.invalid')
JOIN `account` account_row
  ON account_row.user_id = user_row.user_id
 AND account_row.account_type = 'DEMAND_DEPOSIT'
 AND account_row.source_type = 'SEED'
 AND account_row.account_name = '주거래 입출금'
LEFT JOIN financial_profile profile_row ON profile_row.user_id = user_row.user_id;

-- category lookup (V6 reference rows)
DROP TABLE IF EXISTS tmp_dodam_625_categories;
CREATE TABLE tmp_dodam_625_categories (
  code VARCHAR(50) NOT NULL,
  category_id BIGINT NOT NULL,
  PRIMARY KEY (code)
);
INSERT INTO tmp_dodam_625_categories (code, category_id)
SELECT code, category_id
FROM transaction_category
WHERE code IN ('FOOD', 'CAFE', 'GROCERY', 'PUBLIC_TRANSPORT', 'TAXI', 'SHOPPING', 'BEAUTY', 'CULTURE',
               'TRAVEL', 'MEDICAL', 'EDUCATION', 'HOUSING', 'UTILITIES', 'INSURANCE', 'SUBSCRIPTION',
               'LOAN_REPAYMENT', 'SAVING', 'TRANSFER', 'INCOME', 'ETC')
  AND active = 1;

-- subscriptions of the four users as created by DODAM_SUBSCRIPTION_SEED.sql (SUB-U0001-01, SUB-U0002-02, ...)
DROP TABLE IF EXISTS tmp_dodam_625_subscriptions;
CREATE TABLE tmp_dodam_625_subscriptions (
  subscription_id VARCHAR(100) NOT NULL,
  sequence_no INT NOT NULL,
  payment_day TINYINT NOT NULL,
  payment_amount BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  PRIMARY KEY (subscription_id)
);
INSERT INTO tmp_dodam_625_subscriptions (subscription_id, sequence_no, payment_day, payment_amount, status)
SELECT sub.subscription_id, ctx.sequence_no, sub.payment_day, sub.payment_amount, sub.status
FROM subscription sub
JOIN tmp_dodam_625_context ctx ON ctx.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%';

-- ---------------------------------------------------------------------------
-- 2. preflight: every permanent write is gated by ready = 1
--    Expected: 4 demo users, each with a primary card, 20 categories,
--              6 subscriptions (1 per user + 1 more for U0002 / U0004) whose amounts match the 492 formulas,
--              and the August rows of 492 already present (this file extends that window).
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_625_preflight;
CREATE TABLE tmp_dodam_625_preflight (
  ready TINYINT(1) NOT NULL,
  context_user_count INT NOT NULL,
  users_with_card_count INT NOT NULL,
  category_count INT NOT NULL,
  subscription_count INT NOT NULL,
  subscription_amount_mismatch_count INT NOT NULL,
  users_with_august_rows_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (context_user_count = 4),
  CHECK (users_with_card_count = 4),
  CHECK (category_count = 20),
  CHECK (subscription_count = 6),
  CHECK (subscription_amount_mismatch_count = 0),
  CHECK (users_with_august_rows_count = 4)
);
INSERT INTO tmp_dodam_625_preflight (
  ready, context_user_count, users_with_card_count, category_count,
  subscription_count, subscription_amount_mismatch_count, users_with_august_rows_count
)
SELECT
  CASE WHEN checks.context_user_count = 4
        AND checks.users_with_card_count = 4
        AND checks.category_count = 20
        AND checks.subscription_count = 6
        AND checks.subscription_amount_mismatch_count = 0
        AND checks.users_with_august_rows_count = 4
       THEN 1 ELSE 0 END,
  checks.context_user_count,
  checks.users_with_card_count,
  checks.category_count,
  checks.subscription_count,
  checks.subscription_amount_mismatch_count,
  checks.users_with_august_rows_count
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_625_context) AS context_user_count,
    (SELECT COUNT(*) FROM tmp_dodam_625_context WHERE primary_card_id IS NOT NULL) AS users_with_card_count,
    (SELECT COUNT(*) FROM tmp_dodam_625_categories) AS category_count,
    (SELECT COUNT(*) FROM tmp_dodam_625_subscriptions) AS subscription_count,
    (SELECT COUNT(*)
       FROM tmp_dodam_625_subscriptions sub
       JOIN tmp_dodam_625_context ctx ON ctx.sequence_no = sub.sequence_no
      WHERE NOT ((sub.payment_day = 12 AND sub.payment_amount = ctx.subscription_amount_1)
              OR (sub.payment_day = 25 AND sub.payment_amount = ctx.subscription_amount_2))) AS subscription_amount_mismatch_count,
    (SELECT COUNT(DISTINCT tx.user_id)
       FROM `transaction` tx
       JOIN tmp_dodam_625_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED'
        AND tx.transaction_id LIKE 'SEED-U%-202608-%') AS users_with_august_rows_count
) checks;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 3. remove the previous run for these users
-- ---------------------------------------------------------------------------
DELETE tx
FROM `transaction` tx
JOIN tmp_dodam_625_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND tx.transaction_id LIKE 'SEED-SEP-U%'
  AND EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

DELETE history
FROM subscription_payment_history history
JOIN tmp_dodam_625_subscriptions sub ON sub.subscription_id = history.subscription_id
WHERE history.payment_date BETWEEN '2026-09-01' AND '2026-09-30'
  AND EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

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
  CONCAT('SEED-SEP-', ctx.synthetic_user_code, '-', m.ym, '-INC-00'),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL ctx.payday - 1 DAY), '09:30:00', 'INCOME', ctx.monthly_income, NULL,
  '도담컴퍼니', 'ACCOUNT', 'NON_SPENDING', '급여', NULL,
  0, 'SEED', '2026-09-16 09:00:00', NULL
FROM tmp_dodam_625_context ctx
CROSS JOIN tmp_dodam_625_months m
JOIN tmp_dodam_625_categories cat ON cat.code = 'INCOME'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

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
  CONCAT('SEED-SEP-', ctx.synthetic_user_code, '-', m.ym, '-FIX-', fixed.kind),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL fixed.day_of_month - 1 DAY), fixed.tx_time, 'EXPENSE',
  GREATEST(1000, ROUND(ctx.fixed_expense * fixed.share_percent / 100.0
                       * (CASE WHEN fixed.kind = '02' THEN (90 + MOD(ctx.sequence_no * 3 + m.month_no * 7, 21)) / 100.0 ELSE 1 END),
                 -2)),
  NULL,
  fixed.merchant_name, 'ACCOUNT', 'FIXED', fixed.summary, NULL,
  0, 'SEED', '2026-09-16 09:00:00', NULL
FROM tmp_dodam_625_context ctx
CROSS JOIN tmp_dodam_625_months m
CROSS JOIN (
  SELECT '01' AS kind, 'HOUSING' AS category_code, 1 AS day_of_month, 65 AS share_percent, '도담 주거 관리사무소' AS merchant_name, '월세·관리비' AS summary, '10:00:00' AS tx_time
  UNION ALL
  SELECT '02', 'UTILITIES', 5, 20, '도담 통신·공과금', '통신·공과금', '11:00:00'
  UNION ALL
  SELECT '03', 'INSURANCE', 20, 15, '도담 보험', '보험료', '10:30:00'
) fixed
JOIN tmp_dodam_625_categories cat ON cat.code = fixed.category_code
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

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
  CONCAT('SEED-SEP-', ctx.synthetic_user_code, '-', m.ym, '-SUB-', subs.kind),
  ctx.user_id, NULL, ctx.primary_card_id, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL subs.day_of_month - 1 DAY), '00:10:00', 'EXPENSE', subs.amount, NULL,
  subs.merchant_name, 'CARD', 'FIXED', subs.summary, NULL,
  0, 'SEED', '2026-09-16 09:00:00', NULL
FROM tmp_dodam_625_context ctx
CROSS JOIN tmp_dodam_625_months m
JOIN (
  SELECT c.sequence_no, '01' AS kind, 12 AS day_of_month, c.subscription_amount_1 AS amount,
         CASE MOD(c.sequence_no, 3) WHEN 0 THEN '도담 뮤직' WHEN 1 THEN '도담 비디오' ELSE '도담 클라우드' END AS merchant_name,
         '구독 결제' AS summary
  FROM tmp_dodam_625_context c
  UNION ALL
  SELECT c.sequence_no, '02', 25, c.subscription_amount_2, '도담 리딩', '구독 결제'
  FROM tmp_dodam_625_context c
  WHERE c.subscription_amount_2 IS NOT NULL
) subs ON subs.sequence_no = ctx.sequence_no
JOIN tmp_dodam_625_categories cat ON cat.code = 'SUBSCRIPTION'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4d. SAVING transfer on payday + 1 (EXPENSE + NON_SPENDING). payday <= 25 for the demo users, so <= the 26th.
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-SEP-', ctx.synthetic_user_code, '-', m.ym, '-SAV-00'),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL LEAST(ctx.payday, 26) DAY), '09:35:00', 'EXPENSE',
  GREATEST(10000, ROUND(ctx.monthly_income * ctx.saving_ratio / 100.0, -3)), NULL,
  '도담테스트은행 저축계좌', 'ACCOUNT', 'NON_SPENDING', '적금·예금 이체', NULL,
  0, 'SEED', '2026-09-16 09:00:00', NULL
FROM tmp_dodam_625_context ctx
CROSS JOIN tmp_dodam_625_months m
JOIN tmp_dodam_625_categories cat ON cat.code = 'SAVING'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4e. VARIABLE spending slots. Same hash as 492: h = seq*7919 + month*104729 + slot*15485863 (month = 7)
--     amount  = budget / slots * (0.6 .. 1.4)   rounded to 100 won, minimum 1,000 (full-month budget, not prorated)
--     day     = 1 + MOD(h, 27)                  (492 uses 28; capped so nothing lands after the 27th)
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
  CONCAT('SEED-SEP-', ctx.synthetic_user_code, '-', m.ym, '-VAR-', LPAD(s.slot_no, 2, '0')),
  ctx.user_id,
  CASE WHEN MOD(gen.h, 10) < 7 THEN NULL ELSE ctx.primary_account_id END,
  CASE WHEN MOD(gen.h, 10) < 7 THEN ctx.primary_card_id ELSE NULL END,
  cat.category_id,
  DATE_ADD(m.month_start, INTERVAL MOD(gen.h, 27) DAY),
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
  0, 'SEED', '2026-09-16 09:00:00', NULL
FROM tmp_dodam_625_context ctx
CROSS JOIN tmp_dodam_625_months m
JOIN tmp_dodam_625_slots s ON s.slot_no < ctx.variable_slots
JOIN (
  SELECT c.sequence_no, mm.month_no, ss.slot_no,
         (c.sequence_no * 7919 + mm.month_no * 104729 + ss.slot_no * 15485863) AS h
  FROM tmp_dodam_625_context c
  CROSS JOIN tmp_dodam_625_months mm
  CROSS JOIN tmp_dodam_625_slots ss
) gen ON gen.sequence_no = ctx.sequence_no AND gen.month_no = m.month_no AND gen.slot_no = s.slot_no
JOIN tmp_dodam_625_variable_categories pick ON pick.pick_index = MOD(gen.h DIV 7, 16)
JOIN tmp_dodam_625_categories cat ON cat.code = pick.category_code
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 5. subscription side: September charge history + next_payment_date rolled to October
-- ---------------------------------------------------------------------------
INSERT INTO subscription_payment_history (
  subscription_id, payment_date, payment_amount, result_code, retry_count, result_message
)
SELECT
  sub.subscription_id,
  DATE_ADD('2026-09-01', INTERVAL sub.payment_day - 1 DAY),
  sub.payment_amount,
  'SUCCESS',
  0,
  '정상 결제'
FROM tmp_dodam_625_subscriptions sub
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

UPDATE subscription sub
JOIN tmp_dodam_625_subscriptions expected ON expected.subscription_id = sub.subscription_id
SET sub.next_payment_date = DATE_ADD('2026-10-01', INTERVAL sub.payment_day - 1 DAY),
    sub.updated_at = '2026-09-16 09:00:00'
WHERE sub.status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 6. account.last_transaction_date for the four primary accounts (both 492 and this file's ACCOUNT rows)
-- ---------------------------------------------------------------------------
UPDATE `account` account_row
JOIN (
  SELECT tx.account_id, MAX(tx.transaction_date) AS last_date
  FROM `transaction` tx
  JOIN tmp_dodam_625_context ctx ON ctx.primary_account_id = tx.account_id
  WHERE tx.source_type = 'SEED'
    AND (tx.transaction_id LIKE 'SEED-U%' OR tx.transaction_id LIKE 'SEED-SEP-U%')
  GROUP BY tx.account_id
) latest ON latest.account_id = account_row.account_id
SET account_row.last_transaction_date = latest.last_date,
    account_row.updated_at = '2026-09-16 09:00:00'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_625_preflight preflight WHERE preflight.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 7. verification
-- ---------------------------------------------------------------------------
-- Expected preflight row: ready 1, 4 / 4 / 20 / 6 / 0 / 4.
SELECT * FROM tmp_dodam_625_preflight;

-- Expected: INC 4, FIX 12, SUB 6, SAV 4, VAR 64 (total 90); first_date >= 2026-09-01, last_date <= 2026-09-27.
SELECT
  SUBSTRING_INDEX(SUBSTRING_INDEX(tx.transaction_id, '-', 5), '-', -1) AS kind,
  COUNT(*) AS row_count,
  MIN(tx.transaction_date) AS first_date,
  MAX(tx.transaction_date) AS last_date,
  SUM(tx.amount) AS total_amount
FROM `transaction` tx
JOIN tmp_dodam_625_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-SEP-U%'
GROUP BY kind
ORDER BY kind;

-- Expected: one row per user with September spending / income, for a quick look at what the demo will show.
SELECT
  ctx.synthetic_user_code,
  SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END) AS income,
  SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type <> 'NON_SPENDING' THEN tx.amount ELSE 0 END) AS spending,
  SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'NON_SPENDING' THEN tx.amount ELSE 0 END) AS saving_transfer,
  COUNT(*) AS row_count
FROM `transaction` tx
JOIN tmp_dodam_625_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-SEP-U%'
GROUP BY ctx.synthetic_user_code
ORDER BY ctx.synthetic_user_code;

-- Expected: 0 (every row inside 2026-09-01 ~ 2026-09-27, positive amount, valid FK targets, exactly one of account_id / card_id).
SELECT COUNT(*) AS contract_violation_count
FROM `transaction` tx
JOIN tmp_dodam_625_context ctx ON ctx.user_id = tx.user_id
LEFT JOIN `account` a ON a.account_id = tx.account_id AND a.user_id = tx.user_id
LEFT JOIN `card` c ON c.card_id = tx.card_id AND c.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-SEP-U%'
  AND (tx.transaction_date NOT BETWEEN '2026-09-01' AND '2026-09-27'
       OR tx.amount <= 0
       OR tx.category_id IS NULL
       OR (tx.account_id IS NULL AND tx.card_id IS NULL)
       OR (tx.account_id IS NOT NULL AND tx.card_id IS NOT NULL)
       OR (tx.account_id IS NOT NULL AND a.account_id IS NULL)
       OR (tx.card_id IS NOT NULL AND c.card_id IS NULL)
       OR (tx.payment_method = 'CARD' AND tx.card_id IS NULL)
       OR (tx.payment_method = 'ACCOUNT' AND tx.account_id IS NULL));

-- Expected: 6 September history rows, 0 without a matching SUB transaction, next_payment_date 2026-10-12 / 2026-10-25.
SELECT
  COUNT(*) AS september_history_count,
  SUM(tx.transaction_id IS NULL) AS history_without_transaction_count
FROM subscription_payment_history history
JOIN tmp_dodam_625_subscriptions sub ON sub.subscription_id = history.subscription_id
JOIN subscription s ON s.subscription_id = sub.subscription_id
LEFT JOIN `transaction` tx
  ON tx.user_id = s.user_id
 AND tx.transaction_date = history.payment_date
 AND tx.amount = history.payment_amount
 AND tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%'
WHERE history.payment_date BETWEEN '2026-09-01' AND '2026-09-30';

SELECT sub.subscription_id, sub.status, sub.payment_day, sub.next_payment_date
FROM subscription sub
JOIN tmp_dodam_625_subscriptions expected ON expected.subscription_id = sub.subscription_id
ORDER BY sub.subscription_id;

-- Expected: 4 primary accounts with last_transaction_date in 2026-09.
SELECT ctx.synthetic_user_code, account_row.last_transaction_date
FROM `account` account_row
JOIN tmp_dodam_625_context ctx ON ctx.primary_account_id = account_row.account_id
ORDER BY ctx.synthetic_user_code;

-- ---------------------------------------------------------------------------
-- cleanup: scratch tables (regular tables; see header)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_625_categories;
DROP TABLE IF EXISTS tmp_dodam_625_context;
DROP TABLE IF EXISTS tmp_dodam_625_digit;
DROP TABLE IF EXISTS tmp_dodam_625_months;
DROP TABLE IF EXISTS tmp_dodam_625_preflight;
DROP TABLE IF EXISTS tmp_dodam_625_slots;
DROP TABLE IF EXISTS tmp_dodam_625_subscriptions;
DROP TABLE IF EXISTS tmp_dodam_625_variable_categories;
