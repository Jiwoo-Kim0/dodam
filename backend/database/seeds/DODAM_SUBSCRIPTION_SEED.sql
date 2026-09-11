-- DODAM Subscription, SubscriptionPaymentHistory seed
-- Jira: S15P21D105-492
-- Dialect: MySQL 8.x
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07) — tables `subscription`, `subscription_payment_history`
-- Dependency: S15P21D105-491 User/Account seed, S15P21D105-522 Card seed, DODAM_TRANSACTION_SEED.sql (same amounts / dates),
--             V30__create_subscription_and_payment_history.sql (`subscription`, `subscription_payment_history`, added 2026-09-11).
--             Running this file on a DB without V30 fails at the first DELETE with "table does not exist";
--             nothing else is written because every permanent statement runs inside one transaction.
-- Dataset: 6,000 subscriptions (1 per user + 1 more for even-numbered users), 36,000 payment history rows (6 months)
-- Snapshot: 2026-09-07
--
-- Contract with DODAM_TRANSACTION_SEED.sql
-- -----------------------------------------
-- Transaction rows SEED-Uxxxx-yyyymm-SUB-01 (12th) and -SUB-02 (25th, even users) carry the same amounts as the
-- subscriptions below, so "정기결제 목록"(7) and "거래 목록"(6) agree:
--   subscription 1: 9,900 / 12,400 / 14,900 by MOD(seq, 3)  -> 도담 뮤직 / 도담 비디오 / 도담 클라우드, payment_day 12
--   subscription 2: 4,900 (even users)                      -> 도담 리딩, payment_day 25
-- Status: ACTIVE for all, except every 25th user's first subscription is PAUSED (status variety for the API).
-- subscription_id format: SUB-U0001-01 (<= 100 chars). Rerunning deletes and regenerates these ids.

-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references
-- the same TEMPORARY table more than once (self joins, two scalar subqueries,
-- EXISTS gates). This file's scratch tables (tmp_dodam_*) are therefore regular
-- tables, not TEMPORARY, and the cleanup section at the end drops them all. Verified on MySQL 8.0.46.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

DROP TABLE IF EXISTS tmp_dodam_492s_digit;
CREATE TABLE tmp_dodam_492s_digit (digit TINYINT NOT NULL, PRIMARY KEY (digit));
INSERT INTO tmp_dodam_492s_digit (digit) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

DROP TABLE IF EXISTS tmp_dodam_492s_months;
CREATE TABLE tmp_dodam_492s_months (month_no TINYINT NOT NULL, month_start DATE NOT NULL, PRIMARY KEY (month_no));
INSERT INTO tmp_dodam_492s_months (month_no, month_start)
SELECT d.digit + 1, DATE_ADD('2026-03-01', INTERVAL d.digit MONTH)
FROM tmp_dodam_492s_digit d WHERE d.digit BETWEEN 0 AND 5;

-- users + primary card (same resolution as the transaction seed)
DROP TABLE IF EXISTS tmp_dodam_492s_context;
CREATE TABLE tmp_dodam_492s_context (
  sequence_no INT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  user_id BIGINT NOT NULL,
  primary_card_id BIGINT NULL,
  PRIMARY KEY (sequence_no)
);
INSERT INTO tmp_dodam_492s_context (sequence_no, synthetic_user_code, user_id, primary_card_id)
SELECT
  numbers.sequence_no,
  CONCAT('U', LPAD(numbers.sequence_no, 4, '0')),
  user_row.user_id,
  (SELECT MIN(card_row.card_id)
     FROM `card` card_row
     JOIN `account` account_row ON account_row.account_id = card_row.withdrawal_account_id
    WHERE card_row.user_id = user_row.user_id
      AND account_row.account_type = 'DEMAND_DEPOSIT'
      AND account_row.source_type = 'SEED'
      AND account_row.account_name = '주거래 입출금')
FROM (
  SELECT thousands.digit * 1000 + hundreds.digit * 100 + tens.digit * 10 + ones.digit + 1 AS sequence_no
  FROM tmp_dodam_492s_digit thousands
  CROSS JOIN tmp_dodam_492s_digit hundreds
  CROSS JOIN tmp_dodam_492s_digit tens
  CROSS JOIN tmp_dodam_492s_digit ones
) numbers
JOIN `user` user_row
  ON user_row.email = CONCAT('seed.kmeans.u', LPAD(numbers.sequence_no, 4, '0'), '@dodam.invalid')
WHERE numbers.sequence_no BETWEEN 1 AND 4000;

-- expected subscriptions
DROP TABLE IF EXISTS tmp_dodam_492s_expected;
CREATE TABLE tmp_dodam_492s_expected (
  subscription_id VARCHAR(100) NOT NULL,
  sequence_no INT NOT NULL,
  name VARCHAR(150) NOT NULL,
  payment_amount BIGINT NOT NULL,
  payment_day INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  category VARCHAR(50) NOT NULL,
  PRIMARY KEY (subscription_id)
);
INSERT INTO tmp_dodam_492s_expected (subscription_id, sequence_no, name, payment_amount, payment_day, status, category)
SELECT
  CONCAT('SUB-', c.synthetic_user_code, '-01'), c.sequence_no,
  CASE MOD(c.sequence_no, 3) WHEN 0 THEN '도담 뮤직' WHEN 1 THEN '도담 비디오' ELSE '도담 클라우드' END,
  9900 + MOD(c.sequence_no, 3) * 2500, 12,
  CASE WHEN MOD(c.sequence_no, 25) = 0 THEN 'PAUSED' ELSE 'ACTIVE' END,
  CASE MOD(c.sequence_no, 3) WHEN 0 THEN 'MUSIC' WHEN 1 THEN 'VIDEO' ELSE 'CLOUD' END
FROM tmp_dodam_492s_context c
UNION ALL
SELECT
  CONCAT('SUB-', c.synthetic_user_code, '-02'), c.sequence_no,
  '도담 리딩', 4900, 25, 'ACTIVE', 'BOOK'
FROM tmp_dodam_492s_context c
WHERE MOD(c.sequence_no, 2) = 0;

DROP TABLE IF EXISTS tmp_dodam_492s_preflight;
CREATE TABLE tmp_dodam_492s_preflight (
  ready TINYINT(1) NOT NULL,
  user_count INT NOT NULL,
  users_with_card_count INT NOT NULL,
  expected_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (user_count = 4000),
  CHECK (users_with_card_count = 4000),
  CHECK (expected_count = 6000)
);
INSERT INTO tmp_dodam_492s_preflight (ready, user_count, users_with_card_count, expected_count)
SELECT
  CASE WHEN checks.user_count = 4000 AND checks.users_with_card_count = 4000 AND checks.expected_count = 6000 THEN 1 ELSE 0 END,
  checks.user_count, checks.users_with_card_count, checks.expected_count
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_492s_context) AS user_count,
    (SELECT COUNT(*) FROM tmp_dodam_492s_context WHERE primary_card_id IS NOT NULL) AS users_with_card_count,
    (SELECT COUNT(*) FROM tmp_dodam_492s_expected) AS expected_count
) checks;

START TRANSACTION;

-- previous run (history first because of the FK)
DELETE history
FROM subscription_payment_history history
JOIN tmp_dodam_492s_expected expected ON expected.subscription_id = history.subscription_id
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492s_preflight preflight WHERE preflight.ready = 1);

DELETE sub
FROM subscription sub
JOIN tmp_dodam_492s_expected expected ON expected.subscription_id = sub.subscription_id
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492s_preflight preflight WHERE preflight.ready = 1);

INSERT INTO subscription (
  subscription_id, user_id, card_id, name, payment_amount, billing_cycle, payment_day,
  next_payment_date, end_date, status, category, created_at, updated_at, canceled_at
)
SELECT
  expected.subscription_id,
  ctx.user_id,
  ctx.primary_card_id,
  expected.name,
  expected.payment_amount,
  'MONTHLY',
  expected.payment_day,
  -- next charge after the seeded window: September 2026
  CASE WHEN expected.status = 'ACTIVE' THEN DATE_ADD('2026-09-01', INTERVAL expected.payment_day - 1 DAY) ELSE NULL END,
  NULL,
  expected.status,
  expected.category,
  '2026-03-01 09:00:00',
  '2026-09-07 09:00:00',
  NULL
FROM tmp_dodam_492s_expected expected
JOIN tmp_dodam_492s_context ctx ON ctx.sequence_no = expected.sequence_no
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492s_preflight preflight WHERE preflight.ready = 1);

-- one successful charge per month for the six-month window (same dates as the SUB transactions)
INSERT INTO subscription_payment_history (
  subscription_id, payment_date, payment_amount, result_code, retry_count, result_message
)
SELECT
  expected.subscription_id,
  DATE_ADD(m.month_start, INTERVAL expected.payment_day - 1 DAY),
  expected.payment_amount,
  'SUCCESS',
  0,
  '정상 결제'
FROM tmp_dodam_492s_expected expected
CROSS JOIN tmp_dodam_492s_months m
WHERE EXISTS (SELECT 1 FROM tmp_dodam_492s_preflight preflight WHERE preflight.ready = 1);

COMMIT;

-- Expected: 6000 subscriptions (5840 ACTIVE / 160 PAUSED), 36000 history rows, 0 card FK misses.
SELECT status, COUNT(*) AS subscription_count
FROM subscription WHERE subscription_id LIKE 'SUB-U%'
GROUP BY status ORDER BY status;

SELECT COUNT(*) AS history_count
FROM subscription_payment_history WHERE subscription_id LIKE 'SUB-U%';

SELECT COUNT(*) AS card_mismatch_count
FROM subscription sub
LEFT JOIN `card` card_row ON card_row.card_id = sub.card_id AND card_row.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%' AND card_row.card_id IS NULL;

-- Expected: 0 — every history row has a matching SUB transaction on the same date with the same amount.
SELECT COUNT(*) AS history_without_transaction_count
FROM subscription_payment_history history
JOIN subscription sub ON sub.subscription_id = history.subscription_id
LEFT JOIN `transaction` tx
  ON tx.user_id = sub.user_id
 AND tx.transaction_date = history.payment_date
 AND tx.amount = history.payment_amount
 AND tx.transaction_id LIKE 'SEED-U%-SUB-%'
WHERE sub.subscription_id LIKE 'SUB-U%' AND tx.transaction_id IS NULL;

-- ---------------------------------------------------------------------------
-- cleanup: scratch tables and application fixture tables (regular tables; see header)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_492s_context;
DROP TABLE IF EXISTS tmp_dodam_492s_digit;
DROP TABLE IF EXISTS tmp_dodam_492s_expected;
DROP TABLE IF EXISTS tmp_dodam_492s_months;
DROP TABLE IF EXISTS tmp_dodam_492s_preflight;
