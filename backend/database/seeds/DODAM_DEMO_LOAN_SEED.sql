-- DODAM demo user U0003 high-interest loan seed
-- Jira: S15P21D105-633 (시연 계정 데이터 수정 — 3번 계정 고금리 대출 비효율 노출)
-- Dialect: MySQL 8.x
-- Schema basis: V13__create_loan_and_repayment.sql, V10__create_financial_company_product_option.sql
-- Dependency: S15P21D105-491 User/Account seed (U0003 and its ACTIVE demand-deposit account),
--             DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05_COMPAT_FIXED.sql (KB_KOOKMIN 'KB 국민ONE대출'),
--             fixture table below (backend/scripts/make_demo_loan_fixture.py)
-- Dataset: 1 loan + 8 loan repayments (7 PAID 2026-03 ~ 2026-09, 1 SCHEDULED 2026-10-10) for seed.kmeans.u0003
-- Snapshot: 2026-09-18
--
-- Why this file exists
-- --------------------
-- 523 gives loans only to even-numbered seed users, so demo user U0003 (odd) has no loan and the discovery
-- screen never shows LOAN/HIGH_INTEREST_RATE for that account. The demo request asks for U0003 to hold a
-- high-interest loan that the 627 rule flags. This file adds exactly one ACTIVE credit loan whose rate
-- (8.5%) sits 3.271%p above the CREDIT comparison group's disclosed-minimum-rate median (5.229%, 11 bank
-- products, 25~60-month band, equal-payment) -> severity HIGH (>= 2.0%p). 36 installments remain, so the
-- refinance estimate finds an eligible 36-month option (카카오뱅크 신용대출 5.028%) and the LOAN/REFINANCE_LOAN
-- recommendation carries an interest saving (about 741,260 won to maturity, about 247,080 won per year).
--
-- Loan story (all figures follow LoanRepaymentScheduleCalculator: r = 8.5/100/12, HALF_UP rounding)
-- ------------------------------------------------------------------------------------------------
--   KB국민은행 'KB 국민ONE대출' (product loan_type PERSONAL_CREDIT, max limit 20,000,000, disclosed 7.74~8.64%)
--   original 20,000,000 / 60 months / EQUAL_PAYMENT / 8.5000% / repayment day 10
--   start 2024-09-10, maturity 2029-09-10, 24 installments paid (2024-10-10 ~ 2026-09-10), monthly 410,331
--   balance after the 24th payment = 12,998,492, next repayment 2026-10-10, 36 installments remain
--
-- Security prerequisites
-- ----------------------
-- This file never contains the account-number plaintext, an encryption envelope, or keys. Before sourcing it,
-- the same DB must contain a regular (not TEMPORARY) fixture table produced by
-- backend/scripts/make_demo_loan_fixture.py with the application keys of that environment:
--
--   CREATE TABLE tmp_dodam_demo_loan_crypto (
--     seed_loan_code VARCHAR(50) NOT NULL PRIMARY KEY,   -- 'U0003_LOAN_PRIMARY'
--     account_no_encrypted VARBINARY(512) NOT NULL,      -- FieldCryptor: 12-byte IV + AES-256-GCM
--     account_no_hash CHAR(64) NOT NULL,                 -- SensitiveNumberHasher: HMAC-SHA-256 lowercase hex
--     account_no_masked VARCHAR(100) NOT NULL            -- AccountNumberMasker
--   );
--
-- Rerun / ordering contract
-- -------------------------
-- - The loan is keyed by uk_loan_user_account_hash (U0003 + fixture hash): rerunning updates the row in place.
--   Repayment rows of that loan are deleted and rebuilt, so the file is idempotent.
-- - The withdrawal account is U0003's single ACTIVE DEMAND_DEPOSIT account (491). No account_keys fixture is needed.
-- - financial_product_id is resolved by company_code + product_type + product_name and is REQUIRED here (unlike 523):
--   without the linked product the 627 rule cannot determine the comparison group and reports the loan as unavailable.
-- - Preflight: if any check fails, nothing is written (every INSERT is gated by the preflight row).
-- - Independent of 523; a 523 rerun does not touch this loan (different seed code, odd user).
-- - The fixture table is dropped in the cleanup section, so make_demo_loan_fixture.py must run before every rerun.
--
-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references the same TEMPORARY table more
-- than once. Scratch tables are regular tables (tmp_dodam_demo_loan_*) and are dropped at the end.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 1. expected loan (single row of constants)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_loan_expected;
CREATE TABLE tmp_dodam_demo_loan_expected (
  seed_loan_code VARCHAR(50) NOT NULL PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  institution_code VARCHAR(30) NOT NULL,
  institution_name VARCHAR(150) NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  account_name VARCHAR(150) NOT NULL,
  loan_type VARCHAR(30) NOT NULL,
  repayment_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  loan_period INT NOT NULL,
  start_date DATE NOT NULL,
  maturity_date DATE NOT NULL,
  original_principal BIGINT NOT NULL,
  balance BIGINT NOT NULL,
  interest_rate DECIMAL(10,4) NOT NULL,
  repayment_day INT NOT NULL,
  next_repayment_date DATE NOT NULL,
  monthly_amount BIGINT NOT NULL
);

INSERT INTO tmp_dodam_demo_loan_expected VALUES (
  'U0003_LOAN_PRIMARY',
  'seed.kmeans.u0003@dodam.invalid',
  'KB_KOOKMIN', 'KB국민은행',
  'KB 국민ONE대출', 'KB 국민ONE대출',
  'CREDIT', 'EQUAL_PAYMENT', 'ACTIVE',
  60, '2024-09-10', '2029-09-10',
  20000000, 12998492, 8.5000,
  10, '2026-10-10',
  410331
);

-- Repayments inside the seed window (installments 18~24) + the next scheduled installment.
-- principal/interest come from the equal-payment schedule of the original loan (see header).
DROP TABLE IF EXISTS tmp_dodam_demo_loan_expected_repayments;
CREATE TABLE tmp_dodam_demo_loan_expected_repayments (
  repayment_date DATE NOT NULL PRIMARY KEY,
  amount BIGINT NOT NULL,
  principal BIGINT NOT NULL,
  interest BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  CHECK (principal + interest = amount),
  CHECK (status IN ('SCHEDULED', 'PAID', 'FAILED', 'OVERDUE'))
);

INSERT INTO tmp_dodam_demo_loan_expected_repayments (repayment_date, amount, principal, interest, status) VALUES
  ('2026-03-10', 410331, 302916, 107415, 'PAID'),
  ('2026-04-10', 410331, 305061, 105270, 'PAID'),
  ('2026-05-10', 410331, 307222, 103109, 'PAID'),
  ('2026-06-10', 410331, 309398, 100933, 'PAID'),
  ('2026-07-10', 410331, 311590,  98741, 'PAID'),
  ('2026-08-10', 410331, 313797,  96534, 'PAID'),
  ('2026-09-10', 410331, 316020,  94311, 'PAID'),
  ('2026-10-10', 410331, 318258,  92073, 'SCHEDULED');

-- ---------------------------------------------------------------------------
-- 2. preflight (one row when every check passes)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_loan_preflight;
CREATE TABLE tmp_dodam_demo_loan_preflight (
  ready TINYINT NOT NULL PRIMARY KEY,
  user_count INT NOT NULL,
  withdrawal_account_count INT NOT NULL,
  fixture_count INT NOT NULL,
  fixture_hash_valid INT NOT NULL,
  product_count INT NOT NULL,
  foreign_hash_conflicts INT NOT NULL
);

INSERT INTO tmp_dodam_demo_loan_preflight
SELECT
  1,
  metrics.user_count,
  metrics.withdrawal_account_count,
  metrics.fixture_count,
  metrics.fixture_hash_valid,
  metrics.product_count,
  metrics.foreign_hash_conflicts
FROM (
  SELECT
    (SELECT COUNT(*) FROM `user` u
       JOIN tmp_dodam_demo_loan_expected e ON e.user_email = u.email
      WHERE u.deleted_at IS NULL) AS user_count,
    (SELECT COUNT(*) FROM `account` a
       JOIN `user` u ON u.user_id = a.user_id
       JOIN tmp_dodam_demo_loan_expected e ON e.user_email = u.email
      WHERE a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE') AS withdrawal_account_count,
    (SELECT COUNT(*) FROM tmp_dodam_demo_loan_crypto c
       JOIN tmp_dodam_demo_loan_expected e ON e.seed_loan_code = c.seed_loan_code) AS fixture_count,
    (SELECT COUNT(*) FROM tmp_dodam_demo_loan_crypto c
      WHERE c.account_no_hash REGEXP '^[0-9a-f]{64}$'
        AND LENGTH(c.account_no_encrypted) > 12
        AND TRIM(c.account_no_masked) <> '') AS fixture_hash_valid,
    (SELECT COUNT(*) FROM financial_product p
       JOIN financial_company co ON co.company_id = p.company_id
       JOIN tmp_dodam_demo_loan_expected e
         ON e.institution_code = co.company_code AND e.product_name = p.product_name
      WHERE p.product_type = 'LOAN') AS product_count,
    -- the fixture hash must not already belong to another user's loan or account
    (SELECT COUNT(*) FROM loan l
       JOIN tmp_dodam_demo_loan_crypto c ON c.account_no_hash = l.account_no_hash
       JOIN `user` u ON u.user_id = l.user_id
      WHERE u.email <> 'seed.kmeans.u0003@dodam.invalid')
    + (SELECT COUNT(*) FROM `account` a
         JOIN tmp_dodam_demo_loan_crypto c ON c.account_no_hash = a.account_no_hash) AS foreign_hash_conflicts
) metrics
WHERE metrics.user_count = 1
  AND metrics.withdrawal_account_count = 1
  AND metrics.fixture_count = 1
  AND metrics.fixture_hash_valid = 1
  AND metrics.product_count = 1
  AND metrics.foreign_hash_conflicts = 0;

-- Shows the metrics even when preflight fails (then the row is missing and nothing below writes).
SELECT * FROM tmp_dodam_demo_loan_preflight;

-- ---------------------------------------------------------------------------
-- 3. loan (upsert on uk_loan_user_account_hash)
-- ---------------------------------------------------------------------------
INSERT INTO loan (
  user_id, withdrawal_account_id, withdrawal_account_type, financial_product_id,
  institution_code, institution_name,
  account_no_encrypted, account_no_hash, account_no_masked, account_name,
  loan_type, repayment_type, status, loan_period, start_date, maturity_date,
  original_principal, balance, interest_rate, repayment_day, next_repayment_date,
  created_at, updated_at
)
SELECT
  u.user_id, a.account_id, a.account_type, p.product_id,
  e.institution_code, e.institution_name,
  c.account_no_encrypted, LOWER(c.account_no_hash), c.account_no_masked, e.account_name,
  e.loan_type, e.repayment_type, e.status, e.loan_period, e.start_date, e.maturity_date,
  e.original_principal, e.balance, e.interest_rate, e.repayment_day, e.next_repayment_date,
  '2026-09-18 09:00:00', '2026-09-18 09:00:00'
FROM tmp_dodam_demo_loan_expected e
JOIN tmp_dodam_demo_loan_crypto c ON c.seed_loan_code = e.seed_loan_code
JOIN `user` u ON u.email = e.user_email AND u.deleted_at IS NULL
JOIN `account` a ON a.user_id = u.user_id AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
JOIN financial_company co ON co.company_code = e.institution_code
JOIN financial_product p ON p.company_id = co.company_id AND p.product_type = 'LOAN' AND p.product_name = e.product_name
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demo_loan_preflight pf WHERE pf.ready = 1)
ON DUPLICATE KEY UPDATE
  withdrawal_account_id = VALUES(withdrawal_account_id),
  withdrawal_account_type = VALUES(withdrawal_account_type),
  financial_product_id = VALUES(financial_product_id),
  institution_code = VALUES(institution_code),
  institution_name = VALUES(institution_name),
  account_no_encrypted = VALUES(account_no_encrypted),
  account_no_masked = VALUES(account_no_masked),
  account_name = VALUES(account_name),
  loan_type = VALUES(loan_type),
  repayment_type = VALUES(repayment_type),
  status = VALUES(status),
  loan_period = VALUES(loan_period),
  start_date = VALUES(start_date),
  maturity_date = VALUES(maturity_date),
  original_principal = VALUES(original_principal),
  balance = VALUES(balance),
  interest_rate = VALUES(interest_rate),
  repayment_day = VALUES(repayment_day),
  next_repayment_date = VALUES(next_repayment_date),
  updated_at = VALUES(updated_at);

-- ---------------------------------------------------------------------------
-- 4. loan repayments (rebuild for this loan only)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_loan_target;
CREATE TABLE tmp_dodam_demo_loan_target (loan_id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO tmp_dodam_demo_loan_target (loan_id)
SELECT l.loan_id
FROM loan l
JOIN `user` u ON u.user_id = l.user_id
JOIN tmp_dodam_demo_loan_expected e ON e.user_email = u.email
JOIN tmp_dodam_demo_loan_crypto c ON c.seed_loan_code = e.seed_loan_code AND LOWER(c.account_no_hash) = l.account_no_hash
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demo_loan_preflight pf WHERE pf.ready = 1);

DELETE r FROM loan_repayment r
JOIN tmp_dodam_demo_loan_target t ON t.loan_id = r.loan_id;

INSERT INTO loan_repayment (loan_id, repayment_date, amount, principal, interest, status, failure_reason)
SELECT t.loan_id, x.repayment_date, x.amount, x.principal, x.interest, x.status, NULL
FROM tmp_dodam_demo_loan_target t
CROSS JOIN tmp_dodam_demo_loan_expected_repayments x;

-- ---------------------------------------------------------------------------
-- 5. verification
-- ---------------------------------------------------------------------------
-- Expected: 1 row, balance 12998492, rate 8.5000, ACTIVE, product linked (financial_product_id NOT NULL),
--           next_repayment_date 2026-10-10, repayment_count 8 (7 PAID + 1 SCHEDULED).
SELECT
  u.email,
  l.loan_id,
  l.account_name,
  l.account_no_masked,
  l.loan_type,
  l.repayment_type,
  l.status,
  l.loan_period,
  l.start_date,
  l.maturity_date,
  l.original_principal,
  l.balance,
  l.interest_rate,
  l.repayment_day,
  l.next_repayment_date,
  l.financial_product_id,
  (SELECT COUNT(*) FROM loan_repayment r WHERE r.loan_id = l.loan_id) AS repayment_count,
  (SELECT COUNT(*) FROM loan_repayment r WHERE r.loan_id = l.loan_id AND r.status = 'PAID') AS paid_count,
  (SELECT COUNT(*) FROM loan_repayment r WHERE r.loan_id = l.loan_id AND r.status = 'SCHEDULED') AS scheduled_count
FROM loan l
JOIN tmp_dodam_demo_loan_target t ON t.loan_id = l.loan_id
JOIN `user` u ON u.user_id = l.user_id;

-- Expected: 0 (no other demo user gained a loan through this file)
SELECT COUNT(*) AS unexpected_loans_for_other_demo_users
FROM loan l
JOIN `user` u ON u.user_id = l.user_id
WHERE u.email IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0003@dodam.invalid')
  AND l.loan_id NOT IN (SELECT loan_id FROM tmp_dodam_demo_loan_target);

-- ---------------------------------------------------------------------------
-- 6. cleanup (fixture included: regenerate it before a rerun)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demo_loan_target;
DROP TABLE IF EXISTS tmp_dodam_demo_loan_preflight;
DROP TABLE IF EXISTS tmp_dodam_demo_loan_expected_repayments;
DROP TABLE IF EXISTS tmp_dodam_demo_loan_expected;
DROP TABLE IF EXISTS tmp_dodam_demo_loan_crypto;
