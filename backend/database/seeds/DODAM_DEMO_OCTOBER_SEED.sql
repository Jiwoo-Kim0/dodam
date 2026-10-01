-- DODAM demo users (U0001~U0004) October 2026 partial-month transaction seed (2026-10-01 ~ @as_of)
-- Jira: (미발급 — 10월 1일자 시연 데이터)
-- Dialect: MySQL 8.x (window functions)
-- Schema basis: V6__create_transaction_category_and_transaction.sql, V30__create_subscription_and_payment_history.sql
-- Dependency: 491 (users / 주거래 입출금 / financial_profile), 522 (primary card),
--             11번 625 (September rows — this file continues that window),
--             15번 655 (demo subscriptions SUB-U000x-nn with catalog names),
--             17번 679 (U0003 itemised fixed expenses — this file repeats its October items)
-- Dataset: 4 demo users x 1 partial month. With @as_of = 2026-10-01: 19 transactions
--          (FIX 4, VAR 15, SUB 0), 0 subscription charges.
-- Snapshot: 2026-10-01
--
-- Why this file exists
-- --------------------
-- 625 (11번) ends on 2026-09-27 and the app opens on the current month, so from 2026-10-01 the home / ledger screens of the
-- login-enabled demo users show an empty October. This file adds the rows a real statement would carry on the first day(s)
-- of October: rent / management fee auto-debits, a few everyday card payments, and (from the 3rd on) the subscription charges
-- whose payment day has passed. Nothing is dated after @as_of so no seeded row sits in the future.
--
-- @as_of contract
-- ---------------
-- - @as_of is the last day covered, 2026-10-01 by default. Move it forward (up to 2026-10-09) and rerun to extend the window;
--   every section filters by day <= DAY(@as_of).
-- - Days >= 10 are OUT OF SCOPE on purpose: the 10th is U0001's payday and the 633 loan instalment (loan_repayment SCHEDULED
--   2026-10-10), the 15th carries the 678 savings instalments, the 679 card bill (card_billing_statement 2026-10 SCHEDULED)
--   and U0002's payday. Those need SCHEDULED -> PAID flips and new SCHEDULED rows in other tables, which this file does not do.
--   The preflight refuses @as_of outside 2026-10-01 ~ 2026-10-09.
-- - U0003 디즈니+ (unregistered, 27th) is therefore never charged here.
--
-- What this file does
-- -------------------
-- 1. Deletes the previous run for the four users (transaction_id LIKE 'SEED-OCT-U%', source_type = 'SEED') and the October
--    subscription_payment_history rows of their SUB-U% subscriptions, then regenerates.
-- 2. FIXED rows with day <= DAY(@as_of):
--      U0001·U0002·U0004: 625 / 492 formula — HOUSING 65% of financial_profile.fixed_expense on the 1st,
--                         UTILITIES 20% (+-10% jitter, month_no 8) on the 5th. INSURANCE (20th) is out of range.
--      U0003:             679 items — 월세 500,000 on the 1st, 관리비 164,000 (679 seasonal value for October) on the 5th.
--                         The other eight 679 items (10th ~ 26th) are out of range.
-- 3. VARIABLE rows from the explicit day plan below (tmp_dodam_oct_var_plan). U0001·U0002·U0004 keep the 625 merchant names
--    (도담 ○○), U0003 keeps the 636 story merchants (메가MGC커피, 한솥도시락, 배달의민족, GS25 ...). Add rows with a later tx_date
--    (<= the 9th) when @as_of moves forward.
-- 4. SUBSCRIPTION charges for the four users' ACTIVE SUB-U% subscriptions whose payment_day <= DAY(@as_of) (none on the 1st;
--    유튜브 프리미엄 3rd, 배민클럽 6th, 네이버플러스 멤버십 8th follow), each with a SUCCESS payment-history row, and
--    next_payment_date rolled to November for the charged ones (October date kept for the rest, so reruns converge).
-- 5. Updates account.last_transaction_date of the four primary demand-deposit accounts.
--
-- Rerun / ordering contract
-- -------------------------
-- - transaction_id format: SEED-OCT-U0003-202610-VAR-03. The prefix matches neither 492 ('SEED-U%') nor 625 ('SEED-SEP-U%'),
--   so reruns of 6·11·14·15·17 neither delete these rows nor count them as window violations. Rerunning this file alone is
--   idempotent.
-- - Run AFTER 11번, 15번, 17번. If 8번·11번·15번 are rerun (next_payment_date goes back to October, October history is gone)
--   run this file again. 9번 (notification seed) copies the nearest ACTIVE subscription, so rerun 9번 after this file when
--   @as_of >= the 3rd.
-- - No card_billing_statement row is written: October card usage belongs to the November statement, which 679 does not build.
-- - balance_after stays NULL (491 / 705 hold the static balance snapshot); the ledger does not replay balances.
--
-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references the same TEMPORARY table more than once.
-- Scratch tables are regular tables (tmp_dodam_oct_*) and are dropped at the end.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- last covered day. Allowed range 2026-10-01 ~ 2026-10-09 (see header).
SET @as_of := DATE('2026-10-01');

-- ---------------------------------------------------------------------------
-- 0. helpers
-- ---------------------------------------------------------------------------
-- single partial month. month_no 8 is only an input to the 625 utilities jitter; 492's past block uses 7..24 for the hash
-- but that hash is not used here (variable rows come from the explicit plan).
DROP TABLE IF EXISTS tmp_dodam_oct_months;
CREATE TABLE tmp_dodam_oct_months (
  month_no TINYINT NOT NULL,
  month_start DATE NOT NULL,
  ym CHAR(6) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (month_no)
);
INSERT INTO tmp_dodam_oct_months (month_no, month_start, ym, created_at)
VALUES (8, '2026-10-01', '202610', '2026-10-01 09:00:00');

-- ---------------------------------------------------------------------------
-- 1. demo users U0001~U0004 joined to their persisted rows (same resolution as 625 / 655)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_oct_context;
CREATE TABLE tmp_dodam_oct_context (
  sequence_no TINYINT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  user_id BIGINT NOT NULL,
  primary_account_id BIGINT NOT NULL,
  primary_card_id BIGINT NULL,
  fixed_expense BIGINT NOT NULL,
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_tmp_dodam_oct_context_user (user_id)
);
INSERT INTO tmp_dodam_oct_context (sequence_no, synthetic_user_code, user_id, primary_account_id, primary_card_id, fixed_expense)
SELECT
  seed.sequence_no,
  CONCAT('U', LPAD(seed.sequence_no, 4, '0')),
  user_row.user_id,
  account_row.account_id,
  (SELECT MIN(card_row.card_id)
     FROM `card` card_row
    WHERE card_row.user_id = user_row.user_id
      AND card_row.withdrawal_account_id = account_row.account_id),
  COALESCE(profile_row.fixed_expense, 700000)
FROM (SELECT 1 AS sequence_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) seed
JOIN `user` user_row
  ON user_row.email = CONCAT('seed.kmeans.u', LPAD(seed.sequence_no, 4, '0'), '@dodam.invalid')
 AND user_row.deleted_at IS NULL
JOIN `account` account_row
  ON account_row.user_id = user_row.user_id
 AND account_row.account_type = 'DEMAND_DEPOSIT'
 AND account_row.status = 'ACTIVE'
 AND account_row.source_type = 'SEED'
 AND account_row.account_name = '주거래 입출금'
LEFT JOIN financial_profile profile_row ON profile_row.user_id = user_row.user_id;

-- category lookup (V6 reference rows)
DROP TABLE IF EXISTS tmp_dodam_oct_categories;
CREATE TABLE tmp_dodam_oct_categories (
  code VARCHAR(50) NOT NULL,
  category_id BIGINT NOT NULL,
  PRIMARY KEY (code)
);
INSERT INTO tmp_dodam_oct_categories (code, category_id)
SELECT code, category_id
FROM transaction_category
WHERE code IN ('FOOD', 'CAFE', 'GROCERY', 'PUBLIC_TRANSPORT', 'TAXI', 'SHOPPING', 'BEAUTY', 'CULTURE',
               'TRAVEL', 'MEDICAL', 'EDUCATION', 'HOUSING', 'UTILITIES', 'INSURANCE', 'SUBSCRIPTION',
               'LOAN_REPAYMENT', 'SAVING', 'TRANSFER', 'INCOME', 'ETC')
  AND active = 1;

-- ---------------------------------------------------------------------------
-- 2. plans
-- ---------------------------------------------------------------------------
-- 2a. fixed items on days 1..9. U0001·U0002·U0004 follow the 625 formula (share of financial_profile.fixed_expense);
--     U0003 follows 679 (fixed amounts, October column of its seasonal table). Items from the 10th on are out of range.
DROP TABLE IF EXISTS tmp_dodam_oct_fixed_items;
CREATE TABLE tmp_dodam_oct_fixed_items (
  sequence_no TINYINT NOT NULL,
  kind CHAR(2) NOT NULL,
  category_code VARCHAR(50) NOT NULL,
  day_of_month TINYINT NOT NULL,
  tx_time TIME NOT NULL,
  share_percent TINYINT NULL,          -- 625 rule: amount = fixed_expense * share / 100 (kind 02 gets the +-10% jitter)
  fixed_amount BIGINT NULL,            -- 679 rule: literal amount
  merchant_name VARCHAR(255) NOT NULL,
  summary VARCHAR(255) NOT NULL,
  PRIMARY KEY (sequence_no, kind),
  CHECK (day_of_month BETWEEN 1 AND 9),
  CHECK ((share_percent IS NULL) <> (fixed_amount IS NULL))
);
INSERT INTO tmp_dodam_oct_fixed_items
  (sequence_no, kind, category_code, day_of_month, tx_time, share_percent, fixed_amount, merchant_name, summary)
SELECT seq.sequence_no, item.kind, item.category_code, item.day_of_month, item.tx_time, item.share_percent, NULL, item.merchant_name, item.summary
FROM (SELECT 1 AS sequence_no UNION ALL SELECT 2 UNION ALL SELECT 4) seq
CROSS JOIN (
  SELECT '01' AS kind, 'HOUSING' AS category_code, 1 AS day_of_month, CAST('10:00:00' AS TIME) AS tx_time, 65 AS share_percent,
         '도담 주거 관리사무소' AS merchant_name, '월세·관리비' AS summary
  UNION ALL
  SELECT '02', 'UTILITIES', 5, CAST('11:00:00' AS TIME), 20, '도담 통신·공과금', '통신·공과금'
) item
UNION ALL
SELECT 3, '01', 'HOUSING', 1, '07:12:00', NULL, 500000, '한빛오피스텔 임대료',     '월세 자동이체'
UNION ALL
SELECT 3, '02', 'HOUSING', 5, '08:03:00', NULL, 164000, '한빛오피스텔 관리사무소', '관리비';

-- 2b. variable spending, one row per purchase. 2026-10-01 is a Thursday: morning coffee, lunch, an evening delivery or
--     convenience store, a transport top-up. Amounts are multiples of 100 like 492 / 625. payment CARD -> primary card,
--     ACCOUNT -> primary demand-deposit account. Extend with later dates (<= 2026-10-09) together with @as_of.
DROP TABLE IF EXISTS tmp_dodam_oct_var_plan;
CREATE TABLE tmp_dodam_oct_var_plan (
  plan_no INT NOT NULL,
  sequence_no TINYINT NOT NULL,
  tx_date DATE NOT NULL,
  tx_time TIME NOT NULL,
  category_code VARCHAR(50) NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  amount BIGINT NOT NULL,
  payment_method VARCHAR(20) NOT NULL,
  PRIMARY KEY (plan_no),
  CHECK (tx_date BETWEEN '2026-10-01' AND '2026-10-09'),
  CHECK (amount >= 1000 AND MOD(amount, 100) = 0),
  CHECK (payment_method IN ('CARD', 'ACCOUNT'))
);
INSERT INTO tmp_dodam_oct_var_plan (plan_no, sequence_no, tx_date, tx_time, category_code, merchant_name, amount, payment_method) VALUES
  -- U0001 (BALANCED, 625 merchants) — 2026-10-01, 18,700
  (101, 1, '2026-10-01', '08:40:00', 'CAFE',             '도담 커피',            4100, 'CARD'),
  (102, 1, '2026-10-01', '12:30:00', 'FOOD',             '도담 식당',            9000, 'CARD'),
  (103, 1, '2026-10-01', '18:50:00', 'GROCERY',          '도담 편의점',          5600, 'CARD'),
  -- U0002 (SPENDING_FOCUSED, 625 merchants) — 2026-10-01, 82,700
  (201, 2, '2026-10-01', '08:25:00', 'CAFE',             '도담 카페',            6300, 'CARD'),
  (202, 2, '2026-10-01', '12:10:00', 'FOOD',             '도담 한식',           12000, 'CARD'),
  (203, 2, '2026-10-01', '19:20:00', 'FOOD',             '도담 배달',           24500, 'CARD'),
  (204, 2, '2026-10-01', '21:00:00', 'SHOPPING',         '도담 몰',             39900, 'CARD'),
  -- U0003 김싸피 (636 story merchants; September pace FOOD ~17k x 24, CAFE ~6k x 20, GROCERY ~27k x 7) — 2026-10-01, 62,200
  (301, 3, '2026-10-01', '08:12:00', 'CAFE',             '메가MGC커피',          4500, 'CARD'),
  (302, 3, '2026-10-01', '12:24:00', 'FOOD',             '한솥도시락',           7900, 'CARD'),
  (303, 3, '2026-10-01', '13:05:00', 'PUBLIC_TRANSPORT', '티머니 교통카드 충전', 20000, 'ACCOUNT'),
  (304, 3, '2026-10-01', '19:38:00', 'FOOD',             '배달의민족',          23000, 'CARD'),
  (305, 3, '2026-10-01', '21:10:00', 'GROCERY',          'GS25',                 6800, 'CARD'),
  -- U0004 (VOLATILE, 625 merchants) — 2026-10-01, 42,500
  (401, 4, '2026-10-01', '12:45:00', 'FOOD',             '도담 분식',            7500, 'CARD'),
  (402, 4, '2026-10-01', '17:30:00', 'PUBLIC_TRANSPORT', '교통카드 충전',       30000, 'ACCOUNT'),
  (403, 4, '2026-10-01', '20:15:00', 'CAFE',             '도담 카페',            5000, 'CARD');

-- 2c. subscriptions of the four users as created by 655 (SUB-U0001-01 ...). Charged this month when ACTIVE and payment_day <= DAY(@as_of).
DROP TABLE IF EXISTS tmp_dodam_oct_subscriptions;
CREATE TABLE tmp_dodam_oct_subscriptions (
  subscription_id VARCHAR(100) NOT NULL,
  sequence_no TINYINT NOT NULL,
  sub_no VARCHAR(10) NOT NULL,
  name VARCHAR(150) NOT NULL,
  payment_day TINYINT NOT NULL,
  payment_amount BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  charged TINYINT(1) NOT NULL,
  PRIMARY KEY (subscription_id)
);
INSERT INTO tmp_dodam_oct_subscriptions (subscription_id, sequence_no, sub_no, name, payment_day, payment_amount, status, charged)
SELECT
  sub.subscription_id, ctx.sequence_no, SUBSTRING_INDEX(sub.subscription_id, '-', -1), sub.name,
  sub.payment_day, sub.payment_amount, sub.status,
  CASE WHEN sub.status = 'ACTIVE' AND sub.payment_day <= DAY(@as_of) THEN 1 ELSE 0 END
FROM subscription sub
JOIN tmp_dodam_oct_context ctx ON ctx.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%'
  AND sub.payment_day IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. preflight: every permanent write is gated by ready = 1
--    Expected: @as_of inside 2026-10-01 ~ 2026-10-09, 4 demo users each with a primary card, 20 categories,
--              625 September salary rows for all four (this file extends that window), 679 applied to U0003
--              (SEED-SEP-U0003-202609-FIX-10, the September loan row), 655 subscriptions = 23,
--              every plan row pointing at a known user / category.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_oct_preflight;
CREATE TABLE tmp_dodam_oct_preflight (
  ready TINYINT(1) NOT NULL,
  as_of DATE NOT NULL,
  as_of_in_range INT NOT NULL,
  context_user_count INT NOT NULL,
  users_with_card_count INT NOT NULL,
  category_count INT NOT NULL,
  users_with_september_income_count INT NOT NULL,
  u0003_ledger_679_count INT NOT NULL,
  demo_subscription_count INT NOT NULL,
  plan_reference_missing_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (as_of_in_range = 1),
  CHECK (context_user_count = 4),
  CHECK (users_with_card_count = 4),
  CHECK (category_count = 20),
  CHECK (users_with_september_income_count = 4),
  CHECK (u0003_ledger_679_count = 1),
  CHECK (demo_subscription_count = 23),
  CHECK (plan_reference_missing_count = 0)
);
INSERT INTO tmp_dodam_oct_preflight (
  ready, as_of, as_of_in_range, context_user_count, users_with_card_count, category_count,
  users_with_september_income_count, u0003_ledger_679_count, demo_subscription_count, plan_reference_missing_count
)
SELECT
  CASE WHEN checks.as_of_in_range = 1
        AND checks.context_user_count = 4
        AND checks.users_with_card_count = 4
        AND checks.category_count = 20
        AND checks.users_with_september_income_count = 4
        AND checks.u0003_ledger_679_count = 1
        AND checks.demo_subscription_count = 23
        AND checks.plan_reference_missing_count = 0
       THEN 1 ELSE 0 END,
  @as_of,
  checks.as_of_in_range,
  checks.context_user_count,
  checks.users_with_card_count,
  checks.category_count,
  checks.users_with_september_income_count,
  checks.u0003_ledger_679_count,
  checks.demo_subscription_count,
  checks.plan_reference_missing_count
FROM (
  SELECT
    (SELECT CASE WHEN @as_of BETWEEN '2026-10-01' AND '2026-10-09' THEN 1 ELSE 0 END) AS as_of_in_range,
    (SELECT COUNT(*) FROM tmp_dodam_oct_context) AS context_user_count,
    (SELECT COUNT(*) FROM tmp_dodam_oct_context WHERE primary_card_id IS NOT NULL) AS users_with_card_count,
    (SELECT COUNT(*) FROM tmp_dodam_oct_categories) AS category_count,
    (SELECT COUNT(DISTINCT tx.user_id)
       FROM `transaction` tx
       JOIN tmp_dodam_oct_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED'
        AND tx.transaction_type = 'INCOME'
        AND tx.transaction_id LIKE 'SEED-SEP-U%-202609-INC-00') AS users_with_september_income_count,
    (SELECT COUNT(*)
       FROM `transaction` tx
       JOIN tmp_dodam_oct_context ctx ON ctx.user_id = tx.user_id AND ctx.sequence_no = 3
      WHERE tx.source_type = 'SEED'
        AND tx.transaction_id = 'SEED-SEP-U0003-202609-FIX-10') AS u0003_ledger_679_count,
    (SELECT COUNT(*) FROM tmp_dodam_oct_subscriptions) AS demo_subscription_count,
    (SELECT COUNT(*)
       FROM (
         SELECT p.sequence_no, p.category_code FROM tmp_dodam_oct_var_plan p
         UNION ALL
         SELECT f.sequence_no, f.category_code FROM tmp_dodam_oct_fixed_items f
       ) refs
      WHERE NOT EXISTS (SELECT 1 FROM tmp_dodam_oct_context c WHERE c.sequence_no = refs.sequence_no)
         OR NOT EXISTS (SELECT 1 FROM tmp_dodam_oct_categories cat WHERE cat.code = refs.category_code)) AS plan_reference_missing_count
) checks;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 4. remove the previous run for these users
-- ---------------------------------------------------------------------------
DELETE tx
FROM `transaction` tx
JOIN tmp_dodam_oct_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND tx.transaction_id LIKE 'SEED-OCT-U%'
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

DELETE history
FROM subscription_payment_history history
JOIN tmp_dodam_oct_subscriptions sub ON sub.subscription_id = history.subscription_id
WHERE history.payment_date BETWEEN '2026-10-01' AND '2026-10-31'
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 5a. FIXED (account auto-debits). 625 rule for U0001·U0002·U0004, 679 rule for U0003. Only days <= DAY(@as_of).
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-OCT-', ctx.synthetic_user_code, '-', m.ym, '-FIX-', item.kind),
  ctx.user_id, ctx.primary_account_id, NULL, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL item.day_of_month - 1 DAY), item.tx_time, 'EXPENSE',
  CASE
    WHEN item.fixed_amount IS NOT NULL THEN item.fixed_amount
    ELSE GREATEST(1000, ROUND(ctx.fixed_expense * item.share_percent / 100.0
                              * (CASE WHEN item.kind = '02' THEN (90 + MOD(ctx.sequence_no * 3 + m.month_no * 7, 21)) / 100.0 ELSE 1 END),
                        -2))
  END,
  NULL,
  item.merchant_name, 'ACCOUNT', 'FIXED', item.summary, NULL,
  0, 'SEED', m.created_at, NULL
FROM tmp_dodam_oct_fixed_items item
JOIN tmp_dodam_oct_context ctx ON ctx.sequence_no = item.sequence_no
CROSS JOIN tmp_dodam_oct_months m
JOIN tmp_dodam_oct_categories cat ON cat.code = item.category_code
WHERE item.day_of_month <= DAY(@as_of)
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 5b. VARIABLE rows from the plan (tx_date <= @as_of). ids numbered per user in date / time order.
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-OCT-', ctx.synthetic_user_code, '-', m.ym, '-VAR-',
         LPAD(ROW_NUMBER() OVER (PARTITION BY p.sequence_no ORDER BY p.tx_date, p.tx_time, p.plan_no), 2, '0')),
  ctx.user_id,
  CASE WHEN p.payment_method = 'CARD' THEN NULL ELSE ctx.primary_account_id END,
  CASE WHEN p.payment_method = 'CARD' THEN ctx.primary_card_id ELSE NULL END,
  cat.category_id,
  p.tx_date, p.tx_time, 'EXPENSE', p.amount, NULL,
  p.merchant_name, p.payment_method, 'VARIABLE', p.merchant_name, NULL,
  0, 'SEED', m.created_at, NULL
FROM tmp_dodam_oct_var_plan p
JOIN tmp_dodam_oct_context ctx ON ctx.sequence_no = p.sequence_no
CROSS JOIN tmp_dodam_oct_months m
JOIN tmp_dodam_oct_categories cat ON cat.code = p.category_code
WHERE p.tx_date <= @as_of
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 5c. SUBSCRIPTION charges on the primary card (655 shape: 00:10, FIXED, merchant = service name) for payment_day <= DAY(@as_of)
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT('SEED-OCT-', ctx.synthetic_user_code, '-', m.ym, '-SUB-', sub.sub_no),
  ctx.user_id, NULL, ctx.primary_card_id, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL sub.payment_day - 1 DAY), '00:10:00', 'EXPENSE', sub.payment_amount, NULL,
  sub.name, 'CARD', 'FIXED', '구독 결제', NULL,
  0, 'SEED', m.created_at, NULL
FROM tmp_dodam_oct_subscriptions sub
JOIN tmp_dodam_oct_context ctx ON ctx.sequence_no = sub.sequence_no
CROSS JOIN tmp_dodam_oct_months m
JOIN tmp_dodam_oct_categories cat ON cat.code = 'SUBSCRIPTION'
WHERE sub.charged = 1
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 6. subscription side: October charge history for the charged ones, next_payment_date November (charged) / October (not yet)
-- ---------------------------------------------------------------------------
INSERT INTO subscription_payment_history (
  subscription_id, payment_date, payment_amount, result_code, retry_count, result_message
)
SELECT
  sub.subscription_id,
  DATE_ADD('2026-10-01', INTERVAL sub.payment_day - 1 DAY),
  sub.payment_amount,
  'SUCCESS',
  0,
  '정상 결제'
FROM tmp_dodam_oct_subscriptions sub
WHERE sub.charged = 1
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

UPDATE subscription sub
JOIN tmp_dodam_oct_subscriptions expected ON expected.subscription_id = sub.subscription_id
SET sub.next_payment_date = CASE WHEN expected.charged = 1
                                 THEN DATE_ADD('2026-11-01', INTERVAL sub.payment_day - 1 DAY)
                                 ELSE DATE_ADD('2026-10-01', INTERVAL sub.payment_day - 1 DAY) END,
    sub.updated_at = '2026-10-01 09:00:00'
WHERE sub.status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 7. account.last_transaction_date for the four primary accounts (492 + 625 + this file's ACCOUNT rows)
-- ---------------------------------------------------------------------------
UPDATE `account` account_row
JOIN (
  SELECT tx.account_id, MAX(tx.transaction_date) AS last_date
  FROM `transaction` tx
  JOIN tmp_dodam_oct_context ctx ON ctx.primary_account_id = tx.account_id
  WHERE tx.source_type = 'SEED'
    AND (tx.transaction_id LIKE 'SEED-U%' OR tx.transaction_id LIKE 'SEED-SEP-U%' OR tx.transaction_id LIKE 'SEED-OCT-U%')
  GROUP BY tx.account_id
) latest ON latest.account_id = account_row.account_id
SET account_row.last_transaction_date = latest.last_date,
    account_row.updated_at = '2026-10-01 09:00:00'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_oct_preflight preflight WHERE preflight.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 8. verification
-- ---------------------------------------------------------------------------
-- Expected preflight row: ready 1, as_of 2026-10-01, 1 / 4 / 4 / 20 / 4 / 1 / 23 / 0.
SELECT * FROM tmp_dodam_oct_preflight;

-- Expected with @as_of = 2026-10-01: FIX 4 (HOUSING x4), VAR 15, SUB 0 (total 19); first_date = 2026-10-01, last_date <= @as_of.
SELECT
  SUBSTRING_INDEX(SUBSTRING_INDEX(tx.transaction_id, '-', 5), '-', -1) AS kind,
  COUNT(*) AS row_count,
  MIN(tx.transaction_date) AS first_date,
  MAX(tx.transaction_date) AS last_date,
  SUM(tx.amount) AS total_amount
FROM `transaction` tx
JOIN tmp_dodam_oct_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-OCT-U%'
GROUP BY kind
ORDER BY kind;

-- Expected: one row per user. With @as_of = 2026-10-01: U0001 4 rows (fixed 568,100 + 18,700), U0002 4+1, U0003 6 (500,000 + 62,200), U0004 3+1.
SELECT
  ctx.synthetic_user_code,
  SUM(CASE WHEN tx.spending_type = 'FIXED' THEN tx.amount ELSE 0 END) AS fixed_spending,
  SUM(CASE WHEN tx.spending_type = 'VARIABLE' THEN tx.amount ELSE 0 END) AS variable_spending,
  SUM(tx.amount) AS spending,
  COUNT(*) AS row_count
FROM `transaction` tx
JOIN tmp_dodam_oct_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-OCT-U%'
GROUP BY ctx.synthetic_user_code
ORDER BY ctx.synthetic_user_code;

-- Expected: 0 (every row inside 2026-10-01 ~ @as_of, positive amount, valid FK targets, exactly one of account_id / card_id).
SELECT COUNT(*) AS contract_violation_count
FROM `transaction` tx
JOIN tmp_dodam_oct_context ctx ON ctx.user_id = tx.user_id
LEFT JOIN `account` a ON a.account_id = tx.account_id AND a.user_id = tx.user_id
LEFT JOIN `card` c ON c.card_id = tx.card_id AND c.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.transaction_id LIKE 'SEED-OCT-U%'
  AND (tx.transaction_date NOT BETWEEN '2026-10-01' AND @as_of
       OR tx.amount <= 0
       OR tx.category_id IS NULL
       OR (tx.account_id IS NULL AND tx.card_id IS NULL)
       OR (tx.account_id IS NOT NULL AND tx.card_id IS NOT NULL)
       OR (tx.account_id IS NOT NULL AND a.account_id IS NULL)
       OR (tx.card_id IS NOT NULL AND c.card_id IS NULL)
       OR (tx.payment_method = 'CARD' AND tx.card_id IS NULL)
       OR (tx.payment_method = 'ACCOUNT' AND tx.account_id IS NULL));

-- Expected: october_history_count = number of charged subscriptions (0 on the 1st), 0 without a matching SUB transaction.
SELECT
  COUNT(*) AS october_history_count,
  COALESCE(SUM(tx.transaction_id IS NULL), 0) AS history_without_transaction_count
FROM subscription_payment_history history
JOIN tmp_dodam_oct_subscriptions sub ON sub.subscription_id = history.subscription_id
JOIN subscription s ON s.subscription_id = sub.subscription_id
LEFT JOIN `transaction` tx
  ON tx.user_id = s.user_id
 AND tx.transaction_date = history.payment_date
 AND tx.amount = history.payment_amount
 AND tx.transaction_id LIKE 'SEED-OCT-U%-SUB-%'
WHERE history.payment_date BETWEEN '2026-10-01' AND '2026-10-31';

-- Expected: ACTIVE rows with payment_day <= DAY(@as_of) -> 2026-11-xx, the others -> 2026-10-xx, PAUSED -> NULL.
SELECT sub.subscription_id, sub.name, sub.status, sub.payment_day, sub.next_payment_date
FROM subscription sub
JOIN tmp_dodam_oct_subscriptions expected ON expected.subscription_id = sub.subscription_id
ORDER BY sub.subscription_id;

-- Expected: 4 primary accounts with last_transaction_date = 2026-10-01 (HOUSING rows are ACCOUNT payments on the 1st).
SELECT ctx.synthetic_user_code, account_row.last_transaction_date
FROM `account` account_row
JOIN tmp_dodam_oct_context ctx ON ctx.primary_account_id = account_row.account_id
ORDER BY ctx.synthetic_user_code;

-- ---------------------------------------------------------------------------
-- cleanup: scratch tables (regular tables; see header)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_oct_categories;
DROP TABLE IF EXISTS tmp_dodam_oct_context;
DROP TABLE IF EXISTS tmp_dodam_oct_fixed_items;
DROP TABLE IF EXISTS tmp_dodam_oct_months;
DROP TABLE IF EXISTS tmp_dodam_oct_preflight;
DROP TABLE IF EXISTS tmp_dodam_oct_subscriptions;
DROP TABLE IF EXISTS tmp_dodam_oct_var_plan;
