-- DODAM demo user U0003 ("김싸피") two extra demand-deposit accounts (KakaoBank, Shinhan)
-- Jira: S15P21D105-705 (도담 전용 계좌 시연 준비의 일부)
-- Dialect: MySQL 8.x
-- Schema basis: V3 (account)
-- Dependency: 491 (U0003 + SEED '주거래 입출금'), fixture table below (backend/scripts/make_demo_extra_demand_fixture.py)
-- Dataset: 2 DEMAND_DEPOSIT accounts (SEED), U0003 only. No ledger rows.
-- Snapshot: 2026-09-25
--
-- Why this file exists
-- --------------------
-- The asset screen of the demo account should show more than one bank. The 도담 계좌 itself is NOT seeded here: it is a
-- real SSAFY account opened by the app when U0003 connects to the finance network (SafeboxOpenListener), so that the
-- stage transfer 주거래 -> 도담 계좌 goes through the real finance API. See backend/docs/safebox.md §시연 계정 준비.
--
-- Money comes out of 주거래 입출금; total demand-deposit balance stays 17,460,000 (491 snapshot)
-- ---------------------------------------------------------------------------------------------
--   주거래 입출금         17,460,000 -> 15,460,000  (still above the 6.9M floor in demo-ledger-cashflow-679-plan.md §4
--                                                  and above 709's 3,990,000 notebook price)
--   카카오뱅크 입출금통장  1,200,000  KAKAOBANK, static balance like 491's 주거래
--   쏠편한 입출금통장      800,000   SHINHAN_BANK, static balance
-- No transfer rows are written: SAVING transfers count toward the saving rate (EfficiencyScorePolicy SAVING = saving
-- spend / income) and would inflate September. created_at is the snapshot date, so the dormant-account analysis
-- (last activity = created_at when last_transaction_date is NULL) does not flag the two accounts.
-- Run this file BEFORE backend/scripts/prepare_demo_primary_account.py: that script deposits the 주거래 balance of the
-- moment into a real SSAFY account, so the balance must already be 15,460,000.
--
-- Security prerequisites
-- ----------------------
-- This file never contains account-number plaintext, an encryption envelope, or keys. Before sourcing it, the same DB
-- must contain a regular (not TEMPORARY) fixture table produced by backend/scripts/make_demo_extra_demand_fixture.py with
-- the application keys of that environment:
--
--   CREATE TABLE tmp_dodam_demo_extra_demand_crypto (
--     seed_account_code VARCHAR(50) NOT NULL PRIMARY KEY,   -- U0003_DEMAND_KAKAOBANK / U0003_DEMAND_SHINHAN
--     account_no_encrypted VARBINARY(512) NOT NULL,         -- FieldCryptor: 12-byte IV + AES-256-GCM
--     account_no_hash CHAR(64) NOT NULL,                    -- SensitiveNumberHasher: HMAC-SHA-256 lowercase hex
--     account_no_masked VARCHAR(100) NOT NULL               -- AccountNumberMasker
--   );
--
-- Rerun / ordering contract
-- -------------------------
-- - Accounts are keyed by uk_account_user_account_hash, so a rerun updates the same rows and account_id values stay.
-- - 주거래 must hold either 17,460,000 (fresh 491) or 15,460,000 (this file already ran). Any other value means the
--   account moved on stage (transfer, 709 purchase); the preflight then refuses and nothing is written. Reset first.
-- - The 주거래 row is found by name, so it still matches after prepare_demo_primary_account.py replaced its number.
-- - A 491 rerun restores 주거래 to 17,460,000 and its seed account number: rerun this file and the primary script.
-- - Scratch tables are regular tables (tmp_dodam_705_*), dropped in the cleanup section. Never run with `mysql --force`.
--
-- Preflight (one row; ready = 1 only when every check passes, otherwise every write is a no-op)
-- ------------------------------------------------------------------------------------------
--   U0003 1, 주거래 1 with balance 17,460,000 or 15,460,000, fixture 2 valid distinct hashes,
--   no fixture hash on another user / non-demand / non-SEED account.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 1. expected rows
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_705_expected_accounts;
CREATE TABLE tmp_dodam_705_expected_accounts (
  seed_account_code VARCHAR(50) NOT NULL PRIMARY KEY,
  bank_code VARCHAR(20) NOT NULL,
  bank_name VARCHAR(100) NOT NULL,
  account_name VARCHAR(100) NOT NULL,
  balance BIGINT NOT NULL
);
INSERT INTO tmp_dodam_705_expected_accounts VALUES
  ('U0003_DEMAND_KAKAOBANK', 'KAKAOBANK',    '카카오뱅크', '카카오뱅크 입출금통장', 1200000),
  ('U0003_DEMAND_SHINHAN',   'SHINHAN_BANK', '신한은행',   '쏠편한 입출금통장',     800000);

-- ---------------------------------------------------------------------------
-- 2. preflight
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_705_preflight;
CREATE TABLE tmp_dodam_705_preflight AS
SELECT
  CASE WHEN m.user_count = 1
        AND m.primary_count = 1
        AND m.primary_balance_ok = 1
        AND m.fixture_valid_count = 2
        AND m.fixture_distinct_hash_count = 2
        AND m.foreign_hash_conflicts = 0
       THEN 1 ELSE 0 END AS ready,
  m.*
FROM (
  SELECT
    (SELECT COUNT(*) FROM `user` u
      WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND u.deleted_at IS NULL) AS user_count,
    (SELECT COUNT(*) FROM `account` a JOIN `user` u ON u.user_id = a.user_id
      WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
        AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
        AND a.source_type = 'SEED' AND a.account_name = '주거래 입출금') AS primary_count,
    (SELECT COUNT(*) FROM `account` a JOIN `user` u ON u.user_id = a.user_id
      WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
        AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
        AND a.source_type = 'SEED' AND a.account_name = '주거래 입출금'
        AND a.balance IN (17460000, 15460000)) AS primary_balance_ok,
    (SELECT COUNT(*) FROM tmp_dodam_demo_extra_demand_crypto c
       JOIN tmp_dodam_705_expected_accounts e ON e.seed_account_code = c.seed_account_code
      WHERE c.account_no_hash REGEXP '^[0-9a-f]{64}$'
        AND LENGTH(c.account_no_encrypted) > 12
        AND TRIM(c.account_no_masked) <> '') AS fixture_valid_count,
    (SELECT COUNT(DISTINCT LOWER(c.account_no_hash)) FROM tmp_dodam_demo_extra_demand_crypto c
       JOIN tmp_dodam_705_expected_accounts e ON e.seed_account_code = c.seed_account_code)
      AS fixture_distinct_hash_count,
    -- a fixture hash may only belong to a U0003 SEED demand deposit (the rerun case); anything else is a collision
    (SELECT COUNT(*) FROM `account` a
       JOIN tmp_dodam_demo_extra_demand_crypto c ON LOWER(c.account_no_hash) = a.account_no_hash
       JOIN `user` u ON u.user_id = a.user_id
      WHERE u.email <> 'seed.kmeans.u0003@dodam.invalid'
         OR a.account_type <> 'DEMAND_DEPOSIT' OR a.source_type <> 'SEED') AS foreign_hash_conflicts
) m;

SELECT * FROM tmp_dodam_705_preflight;

-- ---------------------------------------------------------------------------
-- 3. permanent writes (single transaction; each statement is gated on the preflight row)
-- ---------------------------------------------------------------------------
START TRANSACTION;

-- 3-1. accounts (upsert on uk_account_user_account_hash -> account_id stays the same on rerun)
INSERT INTO `account` (
  user_id, bank_code, bank_name,
  account_no_encrypted, account_no_hash, account_no_masked, account_name,
  account_type, status, balance, last_transaction_date, currency, source_type,
  created_at, updated_at
)
SELECT
  u.user_id, e.bank_code, e.bank_name,
  c.account_no_encrypted, LOWER(c.account_no_hash), c.account_no_masked, e.account_name,
  'DEMAND_DEPOSIT', 'ACTIVE', e.balance, NULL, 'KRW', 'SEED',
  '2026-09-25 09:00:00', '2026-09-25 09:00:00'
FROM tmp_dodam_705_expected_accounts e
JOIN tmp_dodam_demo_extra_demand_crypto c ON c.seed_account_code = e.seed_account_code
JOIN `user` u ON u.email = 'seed.kmeans.u0003@dodam.invalid' AND u.deleted_at IS NULL
WHERE EXISTS (SELECT 1 FROM tmp_dodam_705_preflight pf WHERE pf.ready = 1)
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

-- 3-2. 주거래 입출금 gives up the 2,000,000 now held by the two accounts (absolute value -> rerun safe)
UPDATE `account` a
JOIN `user` u ON u.user_id = a.user_id
SET a.balance = 15460000,
    a.updated_at = '2026-09-25 09:00:00'
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
  AND a.source_type = 'SEED' AND a.account_name = '주거래 입출금'
  AND EXISTS (SELECT 1 FROM tmp_dodam_705_preflight pf WHERE pf.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 4. verification
-- ---------------------------------------------------------------------------
-- Expected: 주거래 15,460,000 / 카카오뱅크 1,200,000 / 쏠편한 800,000 (+ the 도담 계좌 once the app opened it)
SELECT a.account_id, a.bank_name, a.account_name, a.account_no_masked, a.balance, a.source_type
FROM `account` a
JOIN `user` u ON u.user_id = a.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
ORDER BY a.account_id;

-- Expected: seed_demand_total 17,460,000 (unchanged total of the SEED demand deposits)
SELECT SUM(a.balance) AS seed_demand_total
FROM `account` a
JOIN `user` u ON u.user_id = a.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND a.source_type = 'SEED'
  AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- 5. cleanup (fixture included: regenerate it before a rerun)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_705_preflight;
DROP TABLE IF EXISTS tmp_dodam_705_expected_accounts;
DROP TABLE IF EXISTS tmp_dodam_demo_extra_demand_crypto;
