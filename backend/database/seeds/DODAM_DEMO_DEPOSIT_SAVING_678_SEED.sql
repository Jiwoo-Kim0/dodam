-- DODAM demo user U0003 deposit/savings reinforcement seed (청년도약·주택청약·내집 마련 목돈적금)
-- Jira: S15P21D105-678 (스프린트 311 「[DB] 청년도약·주택청약·내집 마련 예적금 계좌 보강」)
-- Dialect: MySQL 8.x
-- Schema basis: V3 (account), V15 (deposit_saving, savings_payment), V5·V7·V8·V9·V35 (goal 계열), V10 (financial_product)
-- Dependency: 491 User/Account seed (U0003 and its ACTIVE demand-deposit account),
--             DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05_COMPAT_FIXED.sql (SHINHAN_BANK 'SAVINGS' '신한 청년도약계좌'),
--             524 (recommended: 493 links U0003's saving goal to MIN(account_id) among DEPOSIT/SAVINGS SEED accounts,
--                  so 524's DEPOSIT must exist before this file gives U0003 SAVINGS rows), 493 (optional, independent goal),
--             fixture table below (backend/scripts/make_demo_deposit_savings_fixture.py)
-- Dataset: 3 SAVINGS accounts + 3 deposit_saving rows + 210 PAID / 1 SCHEDULED savings_payment rows
--          + 1 saving goal ('내 집 마련 준비', 150,000,000원, 2029-09-21) linked to the 3 accounts + 61 goal_history rows
-- Snapshot: 2026-09-21 (no PAID installment after this date; next installments fall on 2026-10-15)
--
-- Why this file exists
-- --------------------
-- The demo story for U0003 (김싸피) needs a home-purchase saving track: a policy youth savings account, a housing
-- subscription savings account with a long payment history, and a large monthly savings account,
-- all feeding one '내 집 마련 준비' goal. 524 gives odd users one DEPOSIT only, so nothing of this exists yet.
-- The file adds rows for U0003 only and leaves the other 3,999 users, their accounts, products and goals untouched.
--
-- Account story (balance = sum of PAID installments; account.balance never includes interest — 524 convention)
-- -----------------------------------------------------------------------------------------------------------
--   청년도약계좌        SHINHAN_BANK, product '신한 청년도약계좌' (60-month option, base 4.50%)
--                      500,000 x 11 PAID (2025-11-15 ~ 2026-09-15) = 5,500,000 / opened 2025-11-15 / matures 2030-11-15
--   주택청약종합저축    KB_KOOKMIN, no catalog product (financial_product_id NULL)
--                      100,000 x 121 PAID (2016-09-15 ~ 2026-09-15) = 12,100,000 / opened 2016-09-15 / expiry 2036-09-15
--                      + installment 122 SCHEDULED 2026-10-15. Contract rate 2.80% is a demo assumption, not a quoted rate.
--                      A real 주택청약종합저축 has no maturity. The synthetic 20-year expiry exists only because the
--                      cashflow forecast (CashflowEventCollector -> SavingsScheduleCalculator) marks the whole user's
--                      forecast INCOMPLETE_SAVINGS_TERMS for an account without expiry and cannot schedule its future
--                      installments (decision 2026-09-21, option b). Do not present 2036-09-15 as a product term.
--   내집마련 목돈적금   WOORI_BANK, no catalog product (financial_product_id NULL)
--                      1,000,000 x 78 PAID (2020-04-15 ~ 2026-09-15) = 78,000,000 / opened 2020-04-15 / matures 2027-04-15
--                      84-month contract at 3.25% (demo assumption). Maturity forecast API: expected principal 84,000,000,
--                      gross interest 9,669,506, tax 1,489,103, net 8,180,403, expected amount 92,180,403 (as of 2026-09-21).
--   Goal '내 집 마련 준비' SAVING / ACTIVE, target 150,000,000, target date 2029-09-21, current_amount 95,600,000 (63.7333%).
--   The goal is dated 2021-09-21 (created_at marker), not the snapshot date: GoalCalculationService derives the saving pace as
--   current_amount / months since creation (min 1), so a goal created today with 95.6M linked would report 95.6M per month and
--   an achievement date one month away. With 60 elapsed months the pace is 1,593,333/month (close to the real 1,600,000 monthly
--   contribution), expected progress 62.5% vs actual 63.7% (ON_TRACK), required 1,511,112/month (SUFFICIENT).
--   goal_history: GOAL_CREATED on 2021-09-21 plus month-end AMOUNT_UPDATED snapshots 2021-09-30 ~ 2026-08-31 (60 rows), each the
--   cumulative PAID sum of the three accounts up to that date, so the history curve matches the payment rows.
--
-- Not represented by the schema (do not describe them as seeded): 청년도약 정부기여금·비과세·우대조건, 청약 납입인정액·
-- 인정회차·순위·소득공제. savings_payment counts are payment history only, not 청약 인정 횟수. U0003 is 43 in 2026
-- (491/632 birth_date rule), outside the real 청년도약 age band; the account is demo data, not an eligibility claim.
--
-- Why the two synthetic products are NOT added to financial_product
-- ----------------------------------------------------------------
-- Product listing, rate comparison and recommendation queries do not filter source_type, so a synthetic SAVINGS
-- product would enter every user's comparison group. deposit_saving.financial_product_id is nullable (V15, 524),
-- so those two accounts carry NULL and the account_name shows the product name instead.
--
-- Security prerequisites
-- ----------------------
-- This file never contains account-number plaintext, an encryption envelope, or keys. Before sourcing it, the same
-- DB must contain a regular (not TEMPORARY) fixture table produced by backend/scripts/make_demo_deposit_savings_fixture.py
-- with the application keys of that environment:
--
--   CREATE TABLE tmp_dodam_demo_deposit_saving_crypto (
--     seed_account_code VARCHAR(50) NOT NULL PRIMARY KEY,   -- U0003_SAVINGS_YOUTH_LEAP / _HOUSING_SUBSCRIPTION / _HOME_FUND
--     account_no_encrypted VARBINARY(512) NOT NULL,         -- FieldCryptor: 12-byte IV + AES-256-GCM
--     account_no_hash CHAR(64) NOT NULL,                    -- SensitiveNumberHasher: HMAC-SHA-256 lowercase hex
--     account_no_masked VARCHAR(100) NOT NULL               -- AccountNumberMasker
--   );
--
-- Rerun / ordering contract
-- -------------------------
-- - Accounts are keyed by uk_account_user_account_hash (U0003 + fixture hash): a rerun updates the same rows, so
--   account_id values do not change. deposit_saving is keyed by account_id. Payment rows of the three accounts are
--   deleted and rebuilt (savings_payment_id values may change; nothing references them).
-- - The goal is identified by (U0003, type SAVING, created_at '2021-09-21 09:00:00', name). It is inserted once and
--   updated in place afterwards, so goal_id is stable. A user-created goal with the same name but another created_at
--   makes the preflight fail instead of being overwritten.
-- - Links are inserted only when missing. Existing links of other accounts to other goals are never touched.
--   A fixture account already linked to a goal other than the 678 goal fails the preflight.
-- - goal.current_amount is recomputed from the goal's links (contribution_amount, else balance — 626 definition).
-- - Preflight: if any check fails the preflight row carries ready = 0 (metrics stay visible) and every write below
--   is gated on ready = 1, so nothing permanent changes. All permanent writes run in one transaction; the mysql client stops at the first error
--   (never use --force), and the open transaction is rolled back when the connection closes.
-- - Independent of 524/633 (different hashes, odd user). Rerunning 493 keeps this goal (created_at marker differs).
-- - The fixture table is dropped in the cleanup section, so make_demo_deposit_savings_fixture.py must run before every rerun.
--
-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- Scratch tables are regular tables (tmp_dodam_demo_ds_*) because one statement below references the same scratch
-- table more than once (ER_CANT_REOPEN_TABLE 1137 for TEMPORARY tables). They are dropped at the end.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 1. expected rows (constants)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_ds_expected_accounts;
CREATE TABLE tmp_dodam_demo_ds_expected_accounts (
  seed_account_code VARCHAR(50) NOT NULL PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  bank_code VARCHAR(20) NOT NULL,
  bank_name VARCHAR(100) NOT NULL,
  account_name VARCHAR(100) NOT NULL,
  product_company_code VARCHAR(30) NULL,
  product_name VARCHAR(255) NULL,
  status VARCHAR(30) NOT NULL,
  interest_rate DECIMAL(10,4) NOT NULL,
  monthly_amount BIGINT NOT NULL,
  payment_day INT NOT NULL,
  opening_date DATE NOT NULL,
  expiry_date DATE NULL,
  next_payment_date DATE NOT NULL,
  paid_count INT NOT NULL,
  scheduled_count INT NOT NULL,
  balance BIGINT NOT NULL,
  CHECK (balance = monthly_amount * paid_count),
  CHECK (expiry_date IS NULL OR opening_date <= expiry_date)
);

INSERT INTO tmp_dodam_demo_ds_expected_accounts VALUES
  ('U0003_SAVINGS_YOUTH_LEAP', 'seed.kmeans.u0003@dodam.invalid',
   'SHINHAN_BANK', '신한은행', '신한 청년도약계좌', 'SHINHAN_BANK', '신한 청년도약계좌',
   'ACTIVE', 4.5000, 500000, 15, '2025-11-15', '2030-11-15', '2026-10-15', 11, 0, 5500000),
  ('U0003_SAVINGS_HOUSING_SUBSCRIPTION', 'seed.kmeans.u0003@dodam.invalid',
   'KB_KOOKMIN', 'KB국민은행', '주택청약종합저축', NULL, NULL,
   'ACTIVE', 2.8000, 100000, 15, '2016-09-15', '2036-09-15', '2026-10-15', 121, 1, 12100000),
  ('U0003_SAVINGS_HOME_FUND', 'seed.kmeans.u0003@dodam.invalid',
   'WOORI_BANK', '우리은행', '내집마련 목돈적금', NULL, NULL,
   'ACTIVE', 3.2500, 1000000, 15, '2020-04-15', '2027-04-15', '2026-10-15', 78, 0, 78000000);

DROP TABLE IF EXISTS tmp_dodam_demo_ds_expected_goal;
CREATE TABLE tmp_dodam_demo_ds_expected_goal (
  seed_goal_code VARCHAR(50) NOT NULL PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  name VARCHAR(150) NOT NULL,
  status VARCHAR(30) NOT NULL,
  target_amount BIGINT NOT NULL,
  target_date DATE NOT NULL,
  created_at DATETIME NOT NULL,
  CHECK (target_amount > 0)
);

-- created_at doubles as the identity marker of the 678 goal (493 uses '2026-03-01 09:00:00' for its goals) and is the
-- start of the pace / expected-progress axis (see header). Five years before the snapshot date.
INSERT INTO tmp_dodam_demo_ds_expected_goal VALUES
  ('U0003_GOAL_HOME', 'seed.kmeans.u0003@dodam.invalid', '내 집 마련 준비', 'ACTIVE',
   150000000, '2029-09-21', '2021-09-21 09:00:00');

-- Installment rows: installment n (1-based) on opening_date + (n-1) months. PAID up to paid_count, then SCHEDULED.
DROP TABLE IF EXISTS tmp_dodam_demo_ds_expected_payments;
CREATE TABLE tmp_dodam_demo_ds_expected_payments (
  seed_account_code VARCHAR(50) NOT NULL,
  installment_number INT NOT NULL,
  payment_date DATE NOT NULL,
  amount BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  PRIMARY KEY (seed_account_code, installment_number),
  CHECK (status IN ('PAID', 'SCHEDULED'))
);

INSERT INTO tmp_dodam_demo_ds_expected_payments
  (seed_account_code, installment_number, payment_date, amount, status)
SELECT
  e.seed_account_code,
  n.installment_number,
  DATE_ADD(e.opening_date, INTERVAL n.installment_number - 1 MONTH),
  e.monthly_amount,
  CASE WHEN n.installment_number <= e.paid_count THEN 'PAID' ELSE 'SCHEDULED' END
FROM tmp_dodam_demo_ds_expected_accounts e
JOIN (
  SELECT hundreds.d * 100 + tens.d * 10 + ones.d + 1 AS installment_number
  FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
) n ON n.installment_number <= e.paid_count + e.scheduled_count;

-- ---------------------------------------------------------------------------
-- 2. preflight (one row when every check passes)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_ds_preflight;
CREATE TABLE tmp_dodam_demo_ds_preflight (
  ready TINYINT NOT NULL,
  user_count INT NOT NULL,
  withdrawal_account_count INT NOT NULL,
  fixture_count INT NOT NULL,
  fixture_valid_count INT NOT NULL,
  fixture_distinct_hash_count INT NOT NULL,
  product_count INT NOT NULL,
  expected_payment_count INT NOT NULL,
  future_paid_count INT NOT NULL,
  foreign_hash_conflicts INT NOT NULL,
  goal_name_conflicts INT NOT NULL,
  marker_goal_count INT NOT NULL,
  link_conflicts INT NOT NULL
);

INSERT INTO tmp_dodam_demo_ds_preflight
SELECT
  CASE WHEN m.user_count = 1
        AND m.withdrawal_account_count = 1
        AND m.fixture_count = 3
        AND m.fixture_valid_count = 3
        AND m.fixture_distinct_hash_count = 3
        AND m.product_count = 1
        AND m.expected_payment_count = 211
        AND m.future_paid_count = 0
        AND m.foreign_hash_conflicts = 0
        AND m.goal_name_conflicts = 0
        AND m.marker_goal_count <= 1
        AND m.link_conflicts = 0
       THEN 1 ELSE 0 END,
  m.user_count, m.withdrawal_account_count, m.fixture_count, m.fixture_valid_count,
  m.fixture_distinct_hash_count, m.product_count, m.expected_payment_count, m.future_paid_count,
  m.foreign_hash_conflicts, m.goal_name_conflicts, m.marker_goal_count, m.link_conflicts
FROM (
  SELECT
    (SELECT COUNT(*) FROM `user` u
      WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND u.deleted_at IS NULL) AS user_count,
    -- the monthly transfer source: U0003's single seed demand deposit (491 '주거래 입출금'). Restricted to the SEED
    -- row by name so that an account the demo user links later (SSAFY/MANUAL) cannot make this count 2.
    (SELECT COUNT(*) FROM `account` a
       JOIN `user` u ON u.user_id = a.user_id
      WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
        AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
        AND a.source_type = 'SEED' AND a.account_name = '주거래 입출금') AS withdrawal_account_count,
    (SELECT COUNT(*) FROM tmp_dodam_demo_deposit_saving_crypto c
       JOIN tmp_dodam_demo_ds_expected_accounts e ON e.seed_account_code = c.seed_account_code) AS fixture_count,
    (SELECT COUNT(*) FROM tmp_dodam_demo_deposit_saving_crypto c
       JOIN tmp_dodam_demo_ds_expected_accounts e ON e.seed_account_code = c.seed_account_code
      WHERE c.account_no_hash REGEXP '^[0-9a-f]{64}$'
        AND LENGTH(c.account_no_encrypted) > 12
        AND TRIM(c.account_no_masked) <> '') AS fixture_valid_count,
    (SELECT COUNT(DISTINCT LOWER(c.account_no_hash)) FROM tmp_dodam_demo_deposit_saving_crypto c
       JOIN tmp_dodam_demo_ds_expected_accounts e ON e.seed_account_code = c.seed_account_code)
      AS fixture_distinct_hash_count,
    -- every expected product must resolve to exactly one catalog row (no LIMIT 1 guessing)
    (SELECT COUNT(*) FROM tmp_dodam_demo_ds_expected_accounts e
       JOIN financial_company co ON co.company_code = e.product_company_code
       JOIN financial_product p ON p.company_id = co.company_id
        AND p.product_type = 'SAVINGS' AND p.product_name = e.product_name) AS product_count,
    (SELECT COUNT(*) FROM tmp_dodam_demo_ds_expected_payments) AS expected_payment_count,
    -- the story must not pay an installment after the snapshot date
    (SELECT COUNT(*) FROM tmp_dodam_demo_ds_expected_payments x
      WHERE x.status = 'PAID' AND x.payment_date > '2026-09-21') AS future_paid_count,
    -- a fixture hash may only belong to a U0003 SEED SAVINGS account (the rerun case); anything else is a collision
    (SELECT COUNT(*) FROM `account` a
       JOIN tmp_dodam_demo_deposit_saving_crypto c ON LOWER(c.account_no_hash) = a.account_no_hash
       JOIN `user` u ON u.user_id = a.user_id
      WHERE u.email <> 'seed.kmeans.u0003@dodam.invalid'
         OR a.account_type <> 'SAVINGS' OR a.source_type <> 'SEED')
    + (SELECT COUNT(*) FROM loan l
         JOIN tmp_dodam_demo_deposit_saving_crypto c ON LOWER(c.account_no_hash) = l.account_no_hash)
      AS foreign_hash_conflicts,
    -- a same-name goal that is not the 678 goal (e.g. created by the user in the app) must not be silently reused
    (SELECT COUNT(*) FROM goal g
       JOIN `user` u ON u.user_id = g.user_id
       JOIN tmp_dodam_demo_ds_expected_goal eg ON eg.user_email = u.email
      WHERE g.deleted_at IS NULL AND g.name = eg.name AND g.created_at <> eg.created_at) AS goal_name_conflicts,
    (SELECT COUNT(*) FROM goal g
       JOIN `user` u ON u.user_id = g.user_id
       JOIN tmp_dodam_demo_ds_expected_goal eg ON eg.user_email = u.email
      WHERE g.deleted_at IS NULL AND g.type = 'SAVING' AND g.created_at = eg.created_at) AS marker_goal_count,
    -- a fixture account already linked to another goal would violate the one-goal-per-savings rule (V35)
    (SELECT COUNT(*) FROM goal_account ga
       JOIN `account` a ON a.account_id = ga.account_id
       JOIN tmp_dodam_demo_deposit_saving_crypto c ON LOWER(c.account_no_hash) = a.account_no_hash
       JOIN goal g ON g.goal_id = ga.goal_id
       JOIN tmp_dodam_demo_ds_expected_goal eg ON 1 = 1
      WHERE NOT (g.type = 'SAVING' AND g.created_at = eg.created_at AND g.deleted_at IS NULL)) AS link_conflicts
) m;

-- Always one row: ready = 1 only when every check passed. With ready = 0 every write below is a no-op,
-- and the metrics show which check failed.
SELECT * FROM tmp_dodam_demo_ds_preflight;

-- Scratch tables filled inside the transaction. Created here because MySQL DDL commits implicitly and would split
-- the transaction below.
DROP TABLE IF EXISTS tmp_dodam_demo_ds_target;
CREATE TABLE tmp_dodam_demo_ds_target (
  seed_account_code VARCHAR(50) NOT NULL PRIMARY KEY,
  account_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL
);
DROP TABLE IF EXISTS tmp_dodam_demo_ds_goal_target;
CREATE TABLE tmp_dodam_demo_ds_goal_target (
  seed_goal_code VARCHAR(50) NOT NULL PRIMARY KEY,
  goal_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  target_amount BIGINT NOT NULL
);

-- ---------------------------------------------------------------------------
-- 3. permanent writes (single transaction, DML only; each statement is gated on the preflight row)
-- ---------------------------------------------------------------------------
START TRANSACTION;

-- 3-1. account (upsert on uk_account_user_account_hash → account_id stays the same on rerun)
INSERT INTO `account` (
  user_id, bank_code, bank_name,
  account_no_encrypted, account_no_hash, account_no_masked, account_name,
  account_type, status, balance, last_transaction_date, currency, source_type,
  created_at, updated_at
)
SELECT
  u.user_id, e.bank_code, e.bank_name,
  c.account_no_encrypted, LOWER(c.account_no_hash), c.account_no_masked, e.account_name,
  'SAVINGS', e.status, e.balance, NULL, 'KRW', 'SEED',
  '2026-09-21 09:00:00', '2026-09-21 09:00:00'
FROM tmp_dodam_demo_ds_expected_accounts e
JOIN tmp_dodam_demo_deposit_saving_crypto c ON c.seed_account_code = e.seed_account_code
JOIN `user` u ON u.email = e.user_email AND u.deleted_at IS NULL
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demo_ds_preflight pf WHERE pf.ready = 1)
ON DUPLICATE KEY UPDATE
  bank_code = VALUES(bank_code),
  bank_name = VALUES(bank_name),
  account_no_encrypted = VALUES(account_no_encrypted),
  account_no_masked = VALUES(account_no_masked),
  account_name = VALUES(account_name),
  account_type = VALUES(account_type),
  status = VALUES(status),
  balance = VALUES(balance),
  currency = VALUES(currency),
  source_type = VALUES(source_type),
  updated_at = VALUES(updated_at);

-- Resolved account ids of the three fixture accounts (used by every step below and by the verification section).
INSERT INTO tmp_dodam_demo_ds_target (seed_account_code, account_id, user_id)
SELECT e.seed_account_code, a.account_id, a.user_id
FROM tmp_dodam_demo_ds_expected_accounts e
JOIN tmp_dodam_demo_deposit_saving_crypto c ON c.seed_account_code = e.seed_account_code
JOIN `user` u ON u.email = e.user_email AND u.deleted_at IS NULL
JOIN `account` a ON a.user_id = u.user_id AND a.account_no_hash = LOWER(c.account_no_hash) AND a.account_type = 'SAVINGS'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demo_ds_preflight pf WHERE pf.ready = 1);

-- 3-2. deposit_saving (upsert on PK account_id). Withdrawal account = U0003's single ACTIVE demand deposit.
INSERT INTO `deposit_saving` (
  account_id, user_id, withdrawal_account_id, withdrawal_account_type, financial_product_id,
  account_type, interest_rate, monthly_amount, payment_day, next_payment_date,
  opening_date, expiry_date, created_at, updated_at
)
SELECT
  t.account_id, t.user_id, w.account_id, w.account_type, p.product_id,
  'SAVINGS', e.interest_rate, e.monthly_amount, e.payment_day, e.next_payment_date,
  e.opening_date, e.expiry_date, '2026-09-21 09:00:00', '2026-09-21 09:00:00'
FROM tmp_dodam_demo_ds_target t
JOIN tmp_dodam_demo_ds_expected_accounts e ON e.seed_account_code = t.seed_account_code
JOIN `account` w ON w.user_id = t.user_id AND w.account_type = 'DEMAND_DEPOSIT' AND w.status = 'ACTIVE'
  AND w.source_type = 'SEED' AND w.account_name = '주거래 입출금'
LEFT JOIN financial_company co ON co.company_code = e.product_company_code
LEFT JOIN financial_product p
  ON p.company_id = co.company_id AND p.product_type = 'SAVINGS' AND p.product_name = e.product_name
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  withdrawal_account_id = VALUES(withdrawal_account_id),
  withdrawal_account_type = VALUES(withdrawal_account_type),
  financial_product_id = VALUES(financial_product_id),
  account_type = VALUES(account_type),
  interest_rate = VALUES(interest_rate),
  monthly_amount = VALUES(monthly_amount),
  payment_day = VALUES(payment_day),
  next_payment_date = VALUES(next_payment_date),
  opening_date = VALUES(opening_date),
  expiry_date = VALUES(expiry_date),
  updated_at = VALUES(updated_at);

-- 3-3. savings_payment (rebuild for the three accounts only)
DELETE p FROM savings_payment p
JOIN tmp_dodam_demo_ds_target t ON t.account_id = p.deposit_saving_id;

INSERT INTO savings_payment (deposit_saving_id, account_type, payment_date, amount, installment_number, status)
SELECT t.account_id, 'SAVINGS', x.payment_date, x.amount, x.installment_number, x.status
FROM tmp_dodam_demo_ds_target t
JOIN tmp_dodam_demo_ds_expected_payments x ON x.seed_account_code = t.seed_account_code;

-- 3-4. goal (insert once, identified by created_at marker; then update in place)
INSERT INTO goal (user_id, name, type, status, current_amount, created_at, updated_at, deleted_at)
SELECT u.user_id, eg.name, 'SAVING', eg.status, 0, eg.created_at, '2026-09-21 09:00:00', NULL
FROM tmp_dodam_demo_ds_expected_goal eg
JOIN `user` u ON u.email = eg.user_email AND u.deleted_at IS NULL
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demo_ds_preflight pf WHERE pf.ready = 1)
  AND NOT EXISTS (
    SELECT 1 FROM goal g
     WHERE g.user_id = u.user_id AND g.type = 'SAVING' AND g.deleted_at IS NULL AND g.created_at = eg.created_at);

INSERT INTO tmp_dodam_demo_ds_goal_target (seed_goal_code, goal_id, user_id, target_amount)
SELECT eg.seed_goal_code, g.goal_id, g.user_id, eg.target_amount
FROM tmp_dodam_demo_ds_expected_goal eg
JOIN `user` u ON u.email = eg.user_email AND u.deleted_at IS NULL
JOIN goal g ON g.user_id = u.user_id AND g.type = 'SAVING' AND g.deleted_at IS NULL AND g.created_at = eg.created_at
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demo_ds_preflight pf WHERE pf.ready = 1);

UPDATE goal g
JOIN tmp_dodam_demo_ds_goal_target gt ON gt.goal_id = g.goal_id
JOIN tmp_dodam_demo_ds_expected_goal eg ON eg.seed_goal_code = gt.seed_goal_code
SET g.name = eg.name, g.status = eg.status, g.updated_at = '2026-09-21 09:00:00';

INSERT INTO saving_goal (goal_id, goal_type, target_amount, target_date)
SELECT gt.goal_id, 'SAVING', eg.target_amount, eg.target_date
FROM tmp_dodam_demo_ds_goal_target gt
JOIN tmp_dodam_demo_ds_expected_goal eg ON eg.seed_goal_code = gt.seed_goal_code
ON DUPLICATE KEY UPDATE
  target_amount = VALUES(target_amount),
  target_date = VALUES(target_date);

-- 3-5. goal_account: link the three savings accounts (contribution_amount NULL = whole balance, 626 rule)
INSERT INTO goal_account (user_id, goal_id, account_id, account_type, contribution_amount)
SELECT t.user_id, gt.goal_id, t.account_id, 'SAVINGS', NULL
FROM tmp_dodam_demo_ds_target t
JOIN tmp_dodam_demo_ds_goal_target gt ON gt.user_id = t.user_id
WHERE NOT EXISTS (
  SELECT 1 FROM goal_account ga WHERE ga.goal_id = gt.goal_id AND ga.account_id = t.account_id);

-- 3-6. goal.current_amount = secured amount of all links of the goal (626 definition)
UPDATE goal g
JOIN tmp_dodam_demo_ds_goal_target gt ON gt.goal_id = g.goal_id
JOIN (
  SELECT ga.goal_id, SUM(COALESCE(ga.contribution_amount, a.balance)) AS secured
  FROM goal_account ga
  JOIN `account` a ON a.account_id = ga.account_id
  JOIN tmp_dodam_demo_ds_goal_target gt2 ON gt2.goal_id = ga.goal_id
  GROUP BY ga.goal_id
) linked ON linked.goal_id = g.goal_id
SET g.current_amount = GREATEST(0, linked.secured);

-- 3-7. goal_history: creation snapshot + month-end snapshots from the cumulative PAID sums (rebuilt on rerun).
-- Only seed-era rows (created_at <= the snapshot timestamp) are replaced; rows the app adds later are kept.
DELETE h FROM goal_history h
JOIN tmp_dodam_demo_ds_goal_target gt ON gt.goal_id = h.goal_id
WHERE h.event IN ('GOAL_CREATED', 'AMOUNT_UPDATED') AND h.created_at <= '2026-09-21 09:00:00';

INSERT INTO goal_history (goal_id, recorded_date, current_amount, progress_rate, forecast_date, event, created_at)
SELECT
  gt.goal_id, DATE(eg.created_at), COALESCE(paid.amount, 0),
  LEAST(100.0000, ROUND(COALESCE(paid.amount, 0) * 100.0 / gt.target_amount, 4)),
  NULL, 'GOAL_CREATED', eg.created_at
FROM tmp_dodam_demo_ds_goal_target gt
JOIN tmp_dodam_demo_ds_expected_goal eg ON eg.seed_goal_code = gt.seed_goal_code
LEFT JOIN (
  SELECT SUM(x.amount) AS amount
  FROM tmp_dodam_demo_ds_expected_payments x
  JOIN tmp_dodam_demo_ds_expected_goal eg2 ON 1 = 1
  WHERE x.status = 'PAID' AND x.payment_date <= DATE(eg2.created_at)
) paid ON 1 = 1;

-- Month ends from the creation month to the month before the snapshot (2021-09-30 .. 2026-08-31 = 60 rows).
INSERT INTO goal_history (goal_id, recorded_date, current_amount, progress_rate, forecast_date, event, created_at)
SELECT
  gt.goal_id, m.month_end,
  COALESCE((SELECT SUM(x.amount) FROM tmp_dodam_demo_ds_expected_payments x
             WHERE x.status = 'PAID' AND x.payment_date <= m.month_end), 0),
  LEAST(100.0000, ROUND(COALESCE((SELECT SUM(x.amount) FROM tmp_dodam_demo_ds_expected_payments x
             WHERE x.status = 'PAID' AND x.payment_date <= m.month_end), 0) * 100.0 / gt.target_amount, 4)),
  NULL, 'AMOUNT_UPDATED', TIMESTAMP(m.month_end, '23:00:00')
FROM tmp_dodam_demo_ds_goal_target gt
JOIN tmp_dodam_demo_ds_expected_goal eg ON eg.seed_goal_code = gt.seed_goal_code
JOIN (
  SELECT LAST_DAY(DATE_ADD('2021-09-01', INTERVAL tens.d * 10 + ones.d MONTH)) AS month_end
  FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
) m ON m.month_end >= DATE(eg.created_at) AND m.month_end < '2026-09-01';

COMMIT;

-- ---------------------------------------------------------------------------
-- 4. verification
-- ---------------------------------------------------------------------------
-- Expected: 3 rows. balance = paid_sum (5,500,000 / 12,100,000 / 78,000,000), paid_count 11 / 121 / 78,
--           scheduled_count 0 / 1 / 0, future_paid_count 0, product linked only for the youth account,
--           withdrawal_account_id NOT NULL, financial_product_id NULL for the other two.
SELECT
  t.seed_account_code,
  a.account_id,
  a.account_name,
  a.account_no_masked,
  a.account_type,
  a.status,
  a.balance,
  d.interest_rate,
  d.monthly_amount,
  d.payment_day,
  d.next_payment_date,
  d.opening_date,
  d.expiry_date,
  d.withdrawal_account_id,
  d.financial_product_id,
  (SELECT COUNT(*) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'PAID') AS paid_count,
  (SELECT COALESCE(SUM(p.amount), 0) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'PAID') AS paid_sum,
  (SELECT COUNT(*) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'SCHEDULED') AS scheduled_count,
  (SELECT COUNT(*) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'PAID' AND p.payment_date > '2026-09-21') AS future_paid_count
FROM tmp_dodam_demo_ds_target t
JOIN `account` a ON a.account_id = t.account_id
LEFT JOIN deposit_saving d ON d.account_id = a.account_id
ORDER BY t.seed_account_code;

-- Expected: 1 row. current_amount 95600000, target 150000000, linked_count 3, progress 63.7333, history_count 61,
--           created_at 2021-09-21 09:00:00.
SELECT
  g.goal_id, g.name, g.type, g.status, g.current_amount, sg.target_amount, sg.target_date, g.created_at,
  (SELECT COUNT(*) FROM goal_account ga WHERE ga.goal_id = g.goal_id) AS linked_count,
  (SELECT COUNT(*) FROM goal_account ga JOIN tmp_dodam_demo_ds_target t ON t.account_id = ga.account_id
    WHERE ga.goal_id = g.goal_id) AS linked_seed_account_count,
  ROUND(g.current_amount * 100.0 / sg.target_amount, 4) AS progress_rate,
  (SELECT COUNT(*) FROM goal_history h WHERE h.goal_id = g.goal_id) AS history_count
FROM tmp_dodam_demo_ds_goal_target gt
JOIN goal g ON g.goal_id = gt.goal_id
JOIN saving_goal sg ON sg.goal_id = g.goal_id;

-- Expected: 0 (no other user received one of these accounts, and no other goal links them)
SELECT
  (SELECT COUNT(*) FROM `account` a
     JOIN tmp_dodam_demo_deposit_saving_crypto c ON LOWER(c.account_no_hash) = a.account_no_hash
     JOIN `user` u ON u.user_id = a.user_id
    WHERE u.email <> 'seed.kmeans.u0003@dodam.invalid') AS accounts_on_other_users,
  (SELECT COUNT(*) FROM goal_account ga
     JOIN tmp_dodam_demo_ds_target t ON t.account_id = ga.account_id
    WHERE ga.goal_id NOT IN (SELECT goal_id FROM tmp_dodam_demo_ds_goal_target)) AS links_to_other_goals;

-- ---------------------------------------------------------------------------
-- 5. cleanup (fixture included: regenerate it before a rerun)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_ds_goal_target;
DROP TABLE IF EXISTS tmp_dodam_demo_ds_target;
DROP TABLE IF EXISTS tmp_dodam_demo_ds_preflight;
DROP TABLE IF EXISTS tmp_dodam_demo_ds_expected_payments;
DROP TABLE IF EXISTS tmp_dodam_demo_ds_expected_goal;
DROP TABLE IF EXISTS tmp_dodam_demo_ds_expected_accounts;
DROP TABLE IF EXISTS tmp_dodam_demo_deposit_saving_crypto;
