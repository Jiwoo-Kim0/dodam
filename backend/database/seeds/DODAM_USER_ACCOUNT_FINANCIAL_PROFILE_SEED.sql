-- DODAM User, UserSetting, FinancialProfile, DemandDeposit Account seed
-- Jira: S15P21D105-491
-- Dialect: MySQL 8.x
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07)
-- Dataset: 4,000 neutral synthetic users for spending K-Means + 4,001 accounts
-- Snapshot: 2026-09-07
--
-- Security prerequisite
-- ---------------------
-- This file never creates account-number plaintext, guesses an encryption
-- envelope, or stores encryption/HMAC keys. Before sourcing this file, the same
-- MySQL session must contain an application-generated scratch table (a regular table, not TEMPORARY) with this
-- contract:
--
--   CREATE TABLE tmp_dodam_491_account_crypto (
--     seed_account_code VARCHAR(50) NOT NULL,
--     account_no_encrypted VARBINARY(512) NOT NULL,
--     account_no_hash CHAR(64) NOT NULL,
--     account_no_masked VARCHAR(100) NOT NULL
--   );
--
-- It must contain U0001_PRIMARY through U4000_PRIMARY and U0002_INACTIVE.
-- Each encrypted value must come from the application's versioned AES-256-GCM
-- codec. Each hash must be a lowercase HMAC-SHA-256 hex value produced with a
-- separate test key. The masked value must come from the application masker.
-- The same synthetic inputs and test keys are required for every rerun.
--
-- The preflight row exists only when all 4,001 crypto fixtures are present,
-- unique, non-empty, and mapped to the expected account codes. Every permanent
-- INSERT is gated by that row. Missing or malformed fixtures therefore write no
-- domain rows even when a SQL client continues after an error.
--
-- K-Means fixture contract
-- ------------------------
-- U0001 through U0004 are detailed demonstration users. The generation-only
-- behavior code cycles by sequence number and is never persisted to User:
--   0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE.
-- Names and emails deliberately contain no expected cluster label. Raw income,
-- settings, identifiers, and account values must not be model features.
-- Transaction task 492 will create the fixed six-month window 2026-03-01 through
-- 2026-08-31 and then update account.last_transaction_date from Transaction.
--
-- Downstream lookup contract (492, 522, 523, 524)
-- ------------------------------------------------
--   SELECT a.account_id
--   FROM `account` a
--   JOIN `user` u ON u.user_id = a.user_id
--   WHERE u.email = 'seed.kmeans.u0001@dodam.invalid'
--     AND a.account_no_hash = :applicationGeneratedAccountHash
--     AND a.account_type = 'DEMAND_DEPOSIT';

-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references
-- the same TEMPORARY table more than once (self joins, two scalar subqueries,
-- EXISTS gates). This file's scratch tables (tmp_dodam_*) and the application
-- fixture tables above are therefore regular tables, not TEMPORARY, and the
-- cleanup section at the end drops them all. Verified on MySQL 8.0.46.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

DROP TABLE IF EXISTS tmp_dodam_491_digit;
CREATE TABLE tmp_dodam_491_digit (
  digit TINYINT NOT NULL,
  PRIMARY KEY (digit),
  CHECK (digit BETWEEN 0 AND 9)
);

INSERT INTO tmp_dodam_491_digit (digit)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

DROP TABLE IF EXISTS tmp_dodam_491_users;
CREATE TABLE tmp_dodam_491_users (
  sequence_no INT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  expected_behavior_code VARCHAR(30) NOT NULL,
  email VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  notification_enabled TINYINT(1) NOT NULL,
  daily_briefing_enabled TINYINT(1) NOT NULL,
  monthly_report_enabled TINYINT(1) NOT NULL,
  daily_briefing_time TIME NULL,
  timezone VARCHAR(50) NOT NULL,
  monthly_income BIGINT NOT NULL,
  annual_income BIGINT NOT NULL,
  payday INT NOT NULL,
  fixed_expense BIGINT NOT NULL,
  emergency_fund_target BIGINT NOT NULL,
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_tmp_dodam_491_user_code (synthetic_user_code),
  UNIQUE KEY uk_tmp_dodam_491_user_email (email),
  CHECK (sequence_no BETWEEN 1 AND 4000),
  CHECK (expected_behavior_code IN (
    'BALANCED', 'SPENDING_FOCUSED', 'SAVING_FOCUSED', 'VOLATILE'
  )),
  CHECK (notification_enabled IN (0, 1)),
  CHECK (daily_briefing_enabled IN (0, 1)),
  CHECK (monthly_report_enabled IN (0, 1)),
  CHECK (monthly_income >= 0),
  CHECK (annual_income >= 0),
  CHECK (payday BETWEEN 1 AND 31),
  CHECK (fixed_expense >= 0),
  CHECK (emergency_fund_target >= 0)
);

INSERT INTO tmp_dodam_491_users (
  sequence_no,
  synthetic_user_code,
  expected_behavior_code,
  email,
  name,
  notification_enabled,
  daily_briefing_enabled,
  monthly_report_enabled,
  daily_briefing_time,
  timezone,
  monthly_income,
  annual_income,
  payday,
  fixed_expense,
  emergency_fund_target
)
SELECT
  base.sequence_no,
  CONCAT('U', LPAD(base.sequence_no, 4, '0')),
  CASE MOD(base.sequence_no - 1, 4)
    WHEN 0 THEN 'BALANCED'
    WHEN 1 THEN 'SPENDING_FOCUSED'
    WHEN 2 THEN 'SAVING_FOCUSED'
    ELSE 'VOLATILE'
  END,
  CONCAT('seed.kmeans.u', LPAD(base.sequence_no, 4, '0'), '@dodam.invalid'),
  CONCAT('도담 사용자 ', LPAD(base.sequence_no, 4, '0')),
  CASE WHEN MOD(base.sequence_no, 5) = 0 THEN 0 ELSE 1 END,
  CASE WHEN MOD(base.sequence_no, 5) IN (0, 1) THEN 0 ELSE 1 END,
  CASE WHEN MOD(base.sequence_no, 10) IN (0, 1, 2) THEN 0 ELSE 1 END,
  CASE
    WHEN MOD(base.sequence_no, 5) IN (0, 1) THEN NULL
    WHEN MOD(base.sequence_no, 3) = 0 THEN '07:00:00'
    WHEN MOD(base.sequence_no, 3) = 1 THEN '08:00:00'
    ELSE '09:00:00'
  END,
  'Asia/Seoul',
  base.monthly_income,
  base.monthly_income * 12,
  CASE MOD(base.sequence_no - 1, 4)
    WHEN 0 THEN 10
    WHEN 1 THEN 15
    WHEN 2 THEN 20
    ELSE 25
  END,
  FLOOR(base.monthly_income * (25 + MOD(base.sequence_no * 13, 41)) / 100),
  FLOOR(base.monthly_income * (25 + MOD(base.sequence_no * 13, 41)) / 100)
    * (3 + MOD(base.sequence_no * 7, 4))
FROM (
  SELECT
    numbers.sequence_no,
    2300000 + MOD((numbers.sequence_no - 1) * 17, 38) * 100000
      AS monthly_income
  FROM (
    SELECT
      thousands.digit * 1000
        + hundreds.digit * 100
        + tens.digit * 10
        + ones.digit
        + 1 AS sequence_no
    FROM tmp_dodam_491_digit thousands
    CROSS JOIN tmp_dodam_491_digit hundreds
    CROSS JOIN tmp_dodam_491_digit tens
    CROSS JOIN tmp_dodam_491_digit ones
  ) numbers
  WHERE numbers.sequence_no BETWEEN 1 AND 4000
) base;

DROP TABLE IF EXISTS tmp_dodam_491_expected_accounts;
CREATE TABLE tmp_dodam_491_expected_accounts (
  seed_account_code VARCHAR(50) NOT NULL,
  user_email VARCHAR(255) NOT NULL,
  bank_code VARCHAR(20) NOT NULL,
  bank_name VARCHAR(100) NOT NULL,
  account_name VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL,
  balance BIGINT NOT NULL,
  last_transaction_date DATE NULL,
  PRIMARY KEY (seed_account_code),
  UNIQUE KEY uk_tmp_dodam_491_expected_user_code
    (user_email, seed_account_code),
  CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
  CHECK (balance >= 0)
);

INSERT INTO tmp_dodam_491_expected_accounts (
  seed_account_code,
  user_email,
  bank_code,
  bank_name,
  account_name,
  status,
  balance,
  last_transaction_date
)
SELECT
  CONCAT(s.synthetic_user_code, '_PRIMARY'),
  s.email,
  CASE MOD(s.sequence_no - 1, 4)
    WHEN 0 THEN 'SEED_BANK_A'
    WHEN 1 THEN 'SEED_BANK_B'
    WHEN 2 THEN 'SEED_BANK_C'
    ELSE 'SEED_BANK_D'
  END,
  CASE MOD(s.sequence_no - 1, 4)
    WHEN 0 THEN '도담테스트은행 A'
    WHEN 1 THEN '도담테스트은행 B'
    WHEN 2 THEN '도담테스트은행 C'
    ELSE '도담테스트은행 D'
  END,
  '주거래 입출금',
  'ACTIVE',
  MOD(s.sequence_no * 7919, 2001) * 10000,
  NULL
FROM tmp_dodam_491_users s
UNION ALL
SELECT
  'U0002_INACTIVE',
  'seed.kmeans.u0002@dodam.invalid',
  'SEED_BANK_C',
  '도담테스트은행 C',
  '휴면성 입출금',
  'INACTIVE',
  20000,
  NULL;

DROP TABLE IF EXISTS tmp_dodam_491_preflight;
CREATE TABLE tmp_dodam_491_preflight (
  ready TINYINT(1) NOT NULL,
  expected_account_count INT NOT NULL,
  crypto_fixture_count INT NOT NULL,
  matched_account_count INT NOT NULL,
  invalid_crypto_count INT NOT NULL,
  distinct_hash_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (expected_account_count = 4001),
  CHECK (crypto_fixture_count = 4001),
  CHECK (matched_account_count = 4001),
  CHECK (invalid_crypto_count = 0),
  CHECK (distinct_hash_count = 4001)
);

-- Referencing a missing crypto table fails here, before START TRANSACTION.
-- A malformed/incomplete table produces ready=0 and fails the CHECK constraints.
INSERT INTO tmp_dodam_491_preflight (
  ready,
  expected_account_count,
  crypto_fixture_count,
  matched_account_count,
  invalid_crypto_count,
  distinct_hash_count
)
SELECT
  CASE WHEN
    checks.expected_account_count = 4001
      AND checks.crypto_fixture_count = 4001
      AND checks.matched_account_count = 4001
      AND checks.invalid_crypto_count = 0
      AND checks.distinct_hash_count = 4001
    THEN 1
    ELSE 0
  END,
  checks.expected_account_count,
  checks.crypto_fixture_count,
  checks.matched_account_count,
  checks.invalid_crypto_count,
  checks.distinct_hash_count
FROM (
  SELECT
    (SELECT COUNT(*)
     FROM tmp_dodam_491_expected_accounts) AS expected_account_count,
    (SELECT COUNT(*)
     FROM tmp_dodam_491_account_crypto) AS crypto_fixture_count,
    (SELECT COUNT(*)
     FROM tmp_dodam_491_expected_accounts expected
     JOIN tmp_dodam_491_account_crypto crypto
       ON crypto.seed_account_code = expected.seed_account_code)
      AS matched_account_count,
    (SELECT COUNT(*)
     FROM tmp_dodam_491_account_crypto crypto
     LEFT JOIN tmp_dodam_491_expected_accounts expected
       ON expected.seed_account_code = crypto.seed_account_code
     WHERE expected.seed_account_code IS NULL
        OR crypto.account_no_encrypted IS NULL
        OR OCTET_LENGTH(crypto.account_no_encrypted) = 0
        OR crypto.account_no_hash IS NULL
        OR CHAR_LENGTH(crypto.account_no_hash) <> 64
        OR LOWER(crypto.account_no_hash) NOT REGEXP '^[0-9a-f]{64}$'
        OR CAST(crypto.account_no_hash AS BINARY)
          <> CAST(LOWER(crypto.account_no_hash) AS BINARY)
        OR crypto.account_no_masked IS NULL
        OR TRIM(crypto.account_no_masked) = '') AS invalid_crypto_count,
    (SELECT COUNT(DISTINCT LOWER(crypto.account_no_hash))
     FROM tmp_dodam_491_account_crypto crypto) AS distinct_hash_count
) checks;

START TRANSACTION;

-- 1. User: email is the stable natural key. password_hash remains NULL for new
-- rows because the application PasswordEncoder contract is not implemented.
INSERT INTO `user` (
  email,
  password_hash,
  name,
  role,
  created_at,
  updated_at,
  deleted_at
)
SELECT
  seed.email,
  NULL,
  seed.name,
  'USER',
  '2026-09-07 09:00:00',
  '2026-09-07 09:00:00',
  NULL
FROM tmp_dodam_491_users seed
WHERE EXISTS (
  SELECT 1 FROM tmp_dodam_491_preflight preflight WHERE preflight.ready = 1
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role = VALUES(role),
  updated_at = VALUES(updated_at),
  deleted_at = NULL;

-- 2. UserSetting: user_id is always resolved from User.email.
INSERT INTO user_setting (
  user_id,
  notification_enabled,
  daily_briefing_enabled,
  monthly_report_enabled,
  daily_briefing_time,
  timezone,
  updated_at
)
SELECT
  user_row.user_id,
  seed.notification_enabled,
  seed.daily_briefing_enabled,
  seed.monthly_report_enabled,
  seed.daily_briefing_time,
  seed.timezone,
  '2026-09-07 09:00:00'
FROM tmp_dodam_491_users seed
JOIN `user` user_row ON user_row.email = seed.email
WHERE EXISTS (
  SELECT 1 FROM tmp_dodam_491_preflight preflight WHERE preflight.ready = 1
)
ON DUPLICATE KEY UPDATE
  notification_enabled = VALUES(notification_enabled),
  daily_briefing_enabled = VALUES(daily_briefing_enabled),
  monthly_report_enabled = VALUES(monthly_report_enabled),
  daily_briefing_time = VALUES(daily_briefing_time),
  timezone = VALUES(timezone),
  updated_at = VALUES(updated_at);

-- 3. FinancialProfile: all K-Means users receive calculation denominators.
INSERT INTO financial_profile (
  user_id,
  monthly_income,
  annual_income,
  payday,
  fixed_expense,
  emergency_fund_target,
  updated_at
)
SELECT
  user_row.user_id,
  seed.monthly_income,
  seed.annual_income,
  seed.payday,
  seed.fixed_expense,
  seed.emergency_fund_target,
  '2026-09-07 09:00:00'
FROM tmp_dodam_491_users seed
JOIN `user` user_row ON user_row.email = seed.email
WHERE EXISTS (
  SELECT 1 FROM tmp_dodam_491_preflight preflight WHERE preflight.ready = 1
)
ON DUPLICATE KEY UPDATE
  monthly_income = VALUES(monthly_income),
  annual_income = VALUES(annual_income),
  payday = VALUES(payday),
  fixed_expense = VALUES(fixed_expense),
  emergency_fund_target = VALUES(emergency_fund_target),
  updated_at = VALUES(updated_at);

-- 4. Account: task 491 creates DEMAND_DEPOSIT only. Task 492 owns the first
-- non-NULL last_transaction_date update after creating matching transactions.
INSERT INTO `account` (
  user_id,
  bank_code,
  bank_name,
  account_no_encrypted,
  account_no_hash,
  account_no_masked,
  account_name,
  account_type,
  status,
  balance,
  last_transaction_date,
  currency,
  source_type,
  created_at,
  updated_at
)
SELECT
  user_row.user_id,
  expected.bank_code,
  expected.bank_name,
  crypto.account_no_encrypted,
  LOWER(crypto.account_no_hash),
  crypto.account_no_masked,
  expected.account_name,
  'DEMAND_DEPOSIT',
  expected.status,
  expected.balance,
  expected.last_transaction_date,
  'KRW',
  'SEED',
  '2026-09-07 09:00:00',
  '2026-09-07 09:00:00'
FROM tmp_dodam_491_expected_accounts expected
JOIN tmp_dodam_491_account_crypto crypto
  ON crypto.seed_account_code = expected.seed_account_code
JOIN `user` user_row ON user_row.email = expected.user_email
WHERE EXISTS (
  SELECT 1 FROM tmp_dodam_491_preflight preflight WHERE preflight.ready = 1
)
ON DUPLICATE KEY UPDATE
  bank_code = VALUES(bank_code),
  bank_name = VALUES(bank_name),
  account_no_encrypted = VALUES(account_no_encrypted),
  account_no_masked = VALUES(account_no_masked),
  account_name = VALUES(account_name),
  account_type = VALUES(account_type),
  status = VALUES(status),
  balance = VALUES(balance),
  last_transaction_date = VALUES(last_transaction_date),
  currency = VALUES(currency),
  source_type = VALUES(source_type),
  updated_at = VALUES(updated_at);

COMMIT;

-- Expected: 4,000 users/settings/profiles and 4,001 demand-deposit accounts.
SELECT
  (SELECT COUNT(*)
   FROM `user` user_row
   JOIN tmp_dodam_491_users seed ON seed.email = user_row.email)
    AS seeded_user_count,
  (SELECT COUNT(*)
   FROM user_setting setting_row
   JOIN `user` user_row ON user_row.user_id = setting_row.user_id
   JOIN tmp_dodam_491_users seed ON seed.email = user_row.email)
    AS seeded_user_setting_count,
  (SELECT COUNT(*)
   FROM financial_profile profile_row
   JOIN `user` user_row ON user_row.user_id = profile_row.user_id
   JOIN tmp_dodam_491_users seed ON seed.email = user_row.email)
    AS seeded_financial_profile_count,
  (SELECT COUNT(*)
   FROM `account` account_row
   JOIN `user` user_row ON user_row.user_id = account_row.user_id
   JOIN tmp_dodam_491_expected_accounts expected
     ON expected.user_email = user_row.email
   JOIN tmp_dodam_491_account_crypto crypto
     ON crypto.seed_account_code = expected.seed_account_code
    AND LOWER(crypto.account_no_hash) = account_row.account_no_hash)
    AS seeded_account_count;

-- Expected: four balanced generation cohorts of 1,000 users each.
SELECT
  seed.expected_behavior_code,
  COUNT(*) AS generation_cohort_count,
  MIN(seed.monthly_income) AS min_monthly_income,
  MAX(seed.monthly_income) AS max_monthly_income
FROM tmp_dodam_491_users seed
GROUP BY seed.expected_behavior_code
ORDER BY seed.expected_behavior_code;

-- Expected: 0 missing 1:1 relationships and 0 missing primary accounts.
SELECT COUNT(*) AS missing_user_relationship_count
FROM tmp_dodam_491_users seed
LEFT JOIN `user` user_row ON user_row.email = seed.email
LEFT JOIN user_setting setting_row ON setting_row.user_id = user_row.user_id
LEFT JOIN financial_profile profile_row
  ON profile_row.user_id = user_row.user_id
WHERE user_row.user_id IS NULL
   OR setting_row.user_id IS NULL
   OR profile_row.user_id IS NULL;

SELECT COUNT(*) AS missing_account_count
FROM tmp_dodam_491_expected_accounts expected
LEFT JOIN tmp_dodam_491_account_crypto crypto
  ON crypto.seed_account_code = expected.seed_account_code
LEFT JOIN `user` user_row ON user_row.email = expected.user_email
LEFT JOIN `account` account_row
  ON account_row.user_id = user_row.user_id
 AND account_row.account_no_hash = LOWER(crypto.account_no_hash)
WHERE account_row.account_id IS NULL;

-- Expected: 0 natural-key duplicates.
SELECT COUNT(*) AS user_email_duplicate_count
FROM (
  SELECT user_row.email
  FROM `user` user_row
  JOIN tmp_dodam_491_users seed ON seed.email = user_row.email
  GROUP BY user_row.email
  HAVING COUNT(*) > 1
) duplicate_users;

SELECT COUNT(*) AS account_natural_key_duplicate_count
FROM (
  SELECT account_row.user_id, account_row.account_no_hash
  FROM `account` account_row
  JOIN `user` user_row ON user_row.user_id = account_row.user_id
  JOIN tmp_dodam_491_users seed ON seed.email = user_row.email
  WHERE account_row.account_type = 'DEMAND_DEPOSIT'
    AND account_row.source_type = 'SEED'
  GROUP BY account_row.user_id, account_row.account_no_hash
  HAVING COUNT(*) > 1
) duplicate_accounts;

-- Expected: 0 profile, account, crypto, naming, and FK contract violations.
SELECT COUNT(*) AS financial_profile_contract_violation_count
FROM financial_profile profile_row
JOIN `user` user_row ON user_row.user_id = profile_row.user_id
JOIN tmp_dodam_491_users seed ON seed.email = user_row.email
WHERE profile_row.monthly_income < 0
   OR profile_row.annual_income <> profile_row.monthly_income * 12
   OR profile_row.payday NOT BETWEEN 1 AND 31
   OR profile_row.fixed_expense < 0
   OR profile_row.fixed_expense > profile_row.monthly_income
   OR profile_row.emergency_fund_target < profile_row.fixed_expense * 3
   OR profile_row.emergency_fund_target > profile_row.fixed_expense * 6;

SELECT COUNT(*) AS account_contract_violation_count
FROM `account` account_row
JOIN `user` user_row ON user_row.user_id = account_row.user_id
JOIN tmp_dodam_491_expected_accounts expected
  ON expected.user_email = user_row.email
JOIN tmp_dodam_491_account_crypto crypto
  ON crypto.seed_account_code = expected.seed_account_code
 AND LOWER(crypto.account_no_hash) = account_row.account_no_hash
WHERE account_row.account_type <> 'DEMAND_DEPOSIT'
   OR account_row.source_type <> 'SEED'
   OR account_row.balance < 0
   OR account_row.last_transaction_date IS NOT NULL
   OR account_row.account_no_encrypted IS NULL
   OR OCTET_LENGTH(account_row.account_no_encrypted) = 0
   OR CHAR_LENGTH(account_row.account_no_hash) <> 64
   OR account_row.account_no_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR account_row.account_no_masked IS NULL
   OR TRIM(account_row.account_no_masked) = '';

SELECT COUNT(*) AS leaked_behavior_label_count
FROM `user` user_row
JOIN tmp_dodam_491_users seed ON seed.email = user_row.email
WHERE LOWER(user_row.email) REGEXP 'balanced|spending|saving|volatile|debt'
   OR LOWER(user_row.name) REGEXP 'balanced|spending|saving|volatile|debt|균형|소비|저축|부채|변동';

SELECT
  (SELECT COUNT(*)
   FROM user_setting setting_row
   LEFT JOIN `user` user_row ON user_row.user_id = setting_row.user_id
   WHERE user_row.user_id IS NULL) AS user_setting_orphan_count,
  (SELECT COUNT(*)
   FROM financial_profile profile_row
   LEFT JOIN `user` user_row ON user_row.user_id = profile_row.user_id
   WHERE user_row.user_id IS NULL) AS financial_profile_orphan_count,
  (SELECT COUNT(*)
   FROM `account` account_row
   LEFT JOIN `user` user_row ON user_row.user_id = account_row.user_id
   WHERE user_row.user_id IS NULL) AS account_orphan_count;

-- Expected: 4,000 active primary accounts and one inactive demonstration account.
SELECT
  SUM(expected.status = 'ACTIVE') AS expected_active_account_count,
  SUM(expected.status = 'INACTIVE') AS expected_inactive_account_count,
  SUM(expected.status = 'CLOSED') AS expected_closed_account_count
FROM tmp_dodam_491_expected_accounts expected;

-- A non-zero count indicates stale rows, commonly caused by changing the test
-- HMAC key between reruns. Expected: 4,001 until later tasks add account types.
SELECT COUNT(*) AS total_seed_demand_deposit_account_count
FROM `account` account_row
JOIN `user` user_row ON user_row.user_id = account_row.user_id
JOIN tmp_dodam_491_users seed ON seed.email = user_row.email
WHERE account_row.account_type = 'DEMAND_DEPOSIT'
  AND account_row.source_type = 'SEED';

-- ---------------------------------------------------------------------------
-- cleanup: scratch tables and application fixture tables (regular tables; see header)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_491_account_crypto;
DROP TABLE IF EXISTS tmp_dodam_491_digit;
DROP TABLE IF EXISTS tmp_dodam_491_expected_accounts;
DROP TABLE IF EXISTS tmp_dodam_491_preflight;
DROP TABLE IF EXISTS tmp_dodam_491_users;
