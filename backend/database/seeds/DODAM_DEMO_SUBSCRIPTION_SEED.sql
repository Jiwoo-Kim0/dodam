-- DODAM demo users U0001~U0004 realistic subscription seed (구독 목록 화면용)
-- Jira: S15P21D105-655 (구독 목록 화면 백엔드 요청서 P1 — docs/subscription-backend-request.md)
-- Dialect: MySQL 8.x
-- Schema basis: V6__create_transaction_category_and_transaction.sql (`transaction`), V30__create_subscription_and_payment_history.sql
-- Dependency: S15P21D105-491 User/Account seed, S15P21D105-522 Card seed (primary card),
--             DODAM_TRANSACTION_SEED.sql (492, 6번), DODAM_SUBSCRIPTION_SEED.sql (8번), DODAM_DEMO_SEPTEMBER_SEED.sql (625, 11번)
-- Dataset: 4 demo users x 5~7 subscriptions (23 rows) + 1 unregistered recurring charge, 2026-03-01 ~ 2026-09-27
--          (165 SUB transactions, 158 payment history rows). Everything else these users own is untouched.
-- Snapshot: 2026-09-20
--
-- Why this file exists
-- --------------------
-- 8번 gives every user 1 subscription (2 for even users) named 도담 뮤직 / 도담 비디오 / 도담 클라우드 / 도담 리딩. The demo account
-- U0003 (seq ≡ 3 mod 6) therefore shows a single line "도담 뮤직 9,900원" on the 내 구독 보기 screen, which is built around picking
-- several subscriptions and showing the monthly saving. The screen also matches brand icons by service name, and "도담 ○○" matches
-- nothing. This file gives the four login-able demo users 5~7 subscriptions with the names and fees of
-- `SubscriptionServiceCatalog` (backend), so the list, the icons, the ledger (가계부) and the report all show 넷플릭스 / 유튜브 프리미엄 / ...
--
-- Scope decision: only U0001~U0004. Changing all 4,000 users would move the FIXED-spending features the K-Means artifact
-- (`train_cluster_model.py`) was trained on and force a retrain; the 3,996 other users keep the 8번 rows.
--
-- What this file does
-- -------------------
-- 1. Deletes, for the four demo users only, every seed subscription (`SUB-U000x-%`), its payment history, and every seed
--    subscription transaction (`SEED-U000x-%-SUB-%` from 492, `SEED-SEP-U000x-%-SUB-%` from 625).
-- 2. Inserts the plan below: one `subscription` row per registered plan (MONTHLY, primary card, category = catalog category),
--    one SUB transaction per month 2026-03 ~ 2026-09 on the primary card (category SUBSCRIPTION, spending_type FIXED, payment CARD,
--    merchant_name = service name so the catalog matcher and the FE icon rules both hit), and one SUCCESS payment_history row per
--    transaction. `next_payment_date` of ACTIVE rows is the October date (625 already moved September into the past).
-- 3. U0004 유튜브 프리미엄 is PAUSED: charged 2026-03 ~ 2026-06 only, next_payment_date NULL (status variety for the API and the screen).
-- 4. U0003 디즈니+ 9,900 is *not* registered: 7 monthly SUBSCRIPTION-category transactions only, so `GET /subscriptions/candidates`
--    returns exactly one unregistered recurring payment for the demo account once the category / catalog filter (P3) is in place.
--
-- The plan (won / month)                          U0001     U0002     U0003     U0004
-- ------------------------------------------------------------------------------------
--   넷플릭스              17,000  12일  VIDEO       o         o         o         o
--   유튜브 프리미엄        14,900   3일  VIDEO       o         o         o      PAUSED(~6월)
--   쿠팡 와우 멤버십        7,890  25일  SHOPPING    o         o         o         o
--   스포티파이            10,900  15일  MUSIC                 o         o
--   밀리의서재             9,900  18일  BOOK        o                   o         o
--   챗GPT 플러스          29,000  20일  AI                    o         o
--   네이버플러스 멤버십     4,900   8일  SHOPPING    o                   o
--   배민클럽               3,990   6일  DELIVERY              o
--   요기패스               2,900  10일  DELIVERY                                  o
--   디즈니+ (미등록)        9,900  27일  VIDEO                           tx only
--   ACTIVE monthly total                        54,590    83,680    94,490    37,690
-- Every payment day is <= 27 so all September charges fall inside the 625 window (2026-09-01 ~ 09-27).
--
-- Rerun / ordering contract
-- -------------------------
-- - Run AFTER 6번(492), 8번, 11번(625). Rerunning this file alone is idempotent (deletes and regenerates the rows above).
-- - A 6번 rerun puts the flat `SEED-U000x-%-SUB-%` rows back, an 8번 rerun puts `SUB-U000x-01/02` (도담 ○○) back, and an 11번 rerun
--   puts the flat September SUB rows back. Run this file again after any of them.
-- - 11번 alone cannot be rerun after this file: its preflight expects exactly 6 demo subscriptions whose amounts follow the 492
--   formula, so it writes nothing (`ready = 0`). To rebuild September run 8번 → 11번 → this file.
-- - 9번 (notification seed) copies the name of the nearest ACTIVE subscription into the PAYMENT_DUE text. Rerun 9번 after this file
--   so the demo inbox says 넷플릭스 instead of 도담 뮤직.
-- - The 8번 end-of-file check `history_without_transaction_count` only joins `SEED-U%-SUB-%`; September history rows (matched by
--   `SEED-SEP-U%`) show up there as "without transaction" exactly as they already did after 625. This file's own check below joins both.
-- - Preflight: 4 demo users with a primary card, SUBSCRIPTION category present, 24 plan rows, 492 August rows and 625 September
--   INCOME rows present for all four. Otherwise nothing is written.
--
-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references the same TEMPORARY table more than once.
-- Scratch tables are regular tables (tmp_dodam_demosub_*) and are dropped at the end.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 0. context: the four demo users, primary demand-deposit account, primary card (same resolution as 625)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demosub_context;
CREATE TABLE tmp_dodam_demosub_context (
  sequence_no TINYINT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  user_id BIGINT NOT NULL,
  primary_account_id BIGINT NOT NULL,
  primary_card_id BIGINT NULL,
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_tmp_dodam_demosub_context_user (user_id)
);
INSERT INTO tmp_dodam_demosub_context (sequence_no, synthetic_user_code, user_id, primary_account_id, primary_card_id)
SELECT
  seed.sequence_no,
  CONCAT('U', LPAD(seed.sequence_no, 4, '0')),
  user_row.user_id,
  account_row.account_id,
  (SELECT MIN(card_row.card_id)
     FROM `card` card_row
    WHERE card_row.user_id = user_row.user_id
      AND card_row.withdrawal_account_id = account_row.account_id)
FROM (SELECT 1 AS sequence_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) seed
JOIN `user` user_row
  ON user_row.email = CONCAT('seed.kmeans.u', LPAD(seed.sequence_no, 4, '0'), '@dodam.invalid')
 AND user_row.deleted_at IS NULL
JOIN `account` account_row
  ON account_row.user_id = user_row.user_id
 AND account_row.account_type = 'DEMAND_DEPOSIT'
 AND account_row.source_type = 'SEED'
 AND account_row.account_name = '주거래 입출금';

-- months: 1..6 = 2026-03 .. 2026-08 (492 ids), 7 = 2026-09 (625 ids)
DROP TABLE IF EXISTS tmp_dodam_demosub_months;
CREATE TABLE tmp_dodam_demosub_months (
  month_no TINYINT NOT NULL PRIMARY KEY,
  ym CHAR(6) NOT NULL,
  month_start DATE NOT NULL,
  id_prefix VARCHAR(20) NOT NULL
);
INSERT INTO tmp_dodam_demosub_months (month_no, ym, month_start, id_prefix) VALUES
  (1, '202603', '2026-03-01', 'SEED-'),
  (2, '202604', '2026-04-01', 'SEED-'),
  (3, '202605', '2026-05-01', 'SEED-'),
  (4, '202606', '2026-06-01', 'SEED-'),
  (5, '202607', '2026-07-01', 'SEED-'),
  (6, '202608', '2026-08-01', 'SEED-'),
  (7, '202609', '2026-09-01', 'SEED-SEP-');

-- SUBSCRIPTION category (V6 reference row)
DROP TABLE IF EXISTS tmp_dodam_demosub_categories;
CREATE TABLE tmp_dodam_demosub_categories (
  code VARCHAR(50) NOT NULL PRIMARY KEY,
  category_id BIGINT NOT NULL
);
INSERT INTO tmp_dodam_demosub_categories (code, category_id)
SELECT code, category_id FROM transaction_category WHERE code = 'SUBSCRIPTION' AND active = 1;

-- ---------------------------------------------------------------------------
-- 1. plan. name / amount / category come from SubscriptionServiceCatalog (2026-09 fees). merchant_name = name.
--    registered = 0 -> transactions only (no subscription row, no history). last_month_no < 7 -> charges stop after that month.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demosub_plan;
CREATE TABLE tmp_dodam_demosub_plan (
  sequence_no TINYINT NOT NULL,
  sub_no CHAR(2) NOT NULL,
  name VARCHAR(150) NOT NULL,
  service_code VARCHAR(50) NOT NULL,
  category VARCHAR(50) NOT NULL,
  payment_amount BIGINT NOT NULL,
  payment_day TINYINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  registered TINYINT(1) NOT NULL,
  last_month_no TINYINT NOT NULL,
  PRIMARY KEY (sequence_no, sub_no),
  CHECK (payment_day BETWEEN 1 AND 27),
  CHECK (status IN ('ACTIVE', 'PAUSED')),
  CHECK (last_month_no BETWEEN 1 AND 7)
);
INSERT INTO tmp_dodam_demosub_plan
  (sequence_no, sub_no, name, service_code, category, payment_amount, payment_day, status, registered, last_month_no) VALUES
  -- U0001 (5)
  (1, '01', '넷플릭스',           'NETFLIX',         'VIDEO',    17000, 12, 'ACTIVE', 1, 7),
  (1, '02', '유튜브 프리미엄',    'YOUTUBE_PREMIUM', 'VIDEO',    14900,  3, 'ACTIVE', 1, 7),
  (1, '03', '쿠팡 와우 멤버십',   'COUPANG_WOW',     'SHOPPING',  7890, 25, 'ACTIVE', 1, 7),
  (1, '04', '밀리의서재',         'MILLIE',          'BOOK',      9900, 18, 'ACTIVE', 1, 7),
  (1, '05', '네이버플러스 멤버십', 'NAVER_PLUS',      'SHOPPING',  4900,  8, 'ACTIVE', 1, 7),
  -- U0002 (6)
  (2, '01', '넷플릭스',           'NETFLIX',         'VIDEO',    17000, 12, 'ACTIVE', 1, 7),
  (2, '02', '유튜브 프리미엄',    'YOUTUBE_PREMIUM', 'VIDEO',    14900,  3, 'ACTIVE', 1, 7),
  (2, '03', '스포티파이',         'SPOTIFY',         'MUSIC',    10900, 15, 'ACTIVE', 1, 7),
  (2, '04', '쿠팡 와우 멤버십',   'COUPANG_WOW',     'SHOPPING',  7890, 25, 'ACTIVE', 1, 7),
  (2, '05', '챗GPT 플러스',       'CHATGPT_PLUS',    'AI',       29000, 20, 'ACTIVE', 1, 7),
  (2, '06', '배민클럽',           'BAEMIN_CLUB',     'DELIVERY',  3990,  6, 'ACTIVE', 1, 7),
  -- U0003 김싸피 (7 registered + 1 unregistered)
  (3, '01', '넷플릭스',           'NETFLIX',         'VIDEO',    17000, 12, 'ACTIVE', 1, 7),
  (3, '02', '유튜브 프리미엄',    'YOUTUBE_PREMIUM', 'VIDEO',    14900,  3, 'ACTIVE', 1, 7),
  (3, '03', '쿠팡 와우 멤버십',   'COUPANG_WOW',     'SHOPPING',  7890, 25, 'ACTIVE', 1, 7),
  (3, '04', '스포티파이',         'SPOTIFY',         'MUSIC',    10900, 15, 'ACTIVE', 1, 7),
  (3, '05', '밀리의서재',         'MILLIE',          'BOOK',      9900, 18, 'ACTIVE', 1, 7),
  (3, '06', '챗GPT 플러스',       'CHATGPT_PLUS',    'AI',       29000, 20, 'ACTIVE', 1, 7),
  (3, '07', '네이버플러스 멤버십', 'NAVER_PLUS',      'SHOPPING',  4900,  8, 'ACTIVE', 1, 7),
  (3, '08', '디즈니+',            'DISNEY_PLUS',     'VIDEO',     9900, 27, 'ACTIVE', 0, 7),
  -- U0004 (5, one PAUSED since July)
  (4, '01', '넷플릭스',           'NETFLIX',         'VIDEO',    17000, 12, 'ACTIVE', 1, 7),
  (4, '02', '유튜브 프리미엄',    'YOUTUBE_PREMIUM', 'VIDEO',    14900,  3, 'PAUSED', 1, 4),
  (4, '03', '쿠팡 와우 멤버십',   'COUPANG_WOW',     'SHOPPING',  7890, 25, 'ACTIVE', 1, 7),
  (4, '04', '요기패스',           'YOGIYO_PASS',     'DELIVERY',  2900, 10, 'ACTIVE', 1, 7),
  (4, '05', '밀리의서재',         'MILLIE',          'BOOK',      9900, 18, 'ACTIVE', 1, 7);

-- ---------------------------------------------------------------------------
-- 2. preflight: every permanent write is gated by ready = 1
--    Expected: 4 demo users, 4 primary cards, SUBSCRIPTION category 1, plan 24, 492 August rows and 625 September salary for all four.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demosub_preflight;
CREATE TABLE tmp_dodam_demosub_preflight (
  ready TINYINT(1) NOT NULL,
  context_user_count INT NOT NULL,
  users_with_card_count INT NOT NULL,
  subscription_category_count INT NOT NULL,
  plan_count INT NOT NULL,
  users_with_august_rows_count INT NOT NULL,
  users_with_september_income_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (context_user_count = 4),
  CHECK (users_with_card_count = 4),
  CHECK (subscription_category_count = 1),
  CHECK (plan_count = 24),
  CHECK (users_with_august_rows_count = 4),
  CHECK (users_with_september_income_count = 4)
);
INSERT INTO tmp_dodam_demosub_preflight (
  ready, context_user_count, users_with_card_count, subscription_category_count, plan_count,
  users_with_august_rows_count, users_with_september_income_count
)
SELECT
  CASE WHEN checks.context_user_count = 4
        AND checks.users_with_card_count = 4
        AND checks.subscription_category_count = 1
        AND checks.plan_count = 24
        AND checks.users_with_august_rows_count = 4
        AND checks.users_with_september_income_count = 4
       THEN 1 ELSE 0 END,
  checks.context_user_count,
  checks.users_with_card_count,
  checks.subscription_category_count,
  checks.plan_count,
  checks.users_with_august_rows_count,
  checks.users_with_september_income_count
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_demosub_context) AS context_user_count,
    (SELECT COUNT(*) FROM tmp_dodam_demosub_context WHERE primary_card_id IS NOT NULL) AS users_with_card_count,
    (SELECT COUNT(*) FROM tmp_dodam_demosub_categories) AS subscription_category_count,
    (SELECT COUNT(*) FROM tmp_dodam_demosub_plan) AS plan_count,
    (SELECT COUNT(DISTINCT tx.user_id)
       FROM `transaction` tx
       JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED'
        AND tx.transaction_id LIKE 'SEED-U%-202608-%') AS users_with_august_rows_count,
    (SELECT COUNT(DISTINCT tx.user_id)
       FROM `transaction` tx
       JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED'
        AND tx.transaction_id LIKE 'SEED-SEP-U%-202609-INC-%') AS users_with_september_income_count
) checks;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 3. remove the previous state for these users: 8번 / earlier runs of this file (subscriptions + history), 492 / 625 / earlier
--    runs of this file (SUB transactions). Nothing outside the four users, nothing that is not a seed row.
-- ---------------------------------------------------------------------------
DELETE history
FROM subscription_payment_history history
JOIN subscription sub ON sub.subscription_id = history.subscription_id
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%'
  AND EXISTS (SELECT 1 FROM tmp_dodam_demosub_preflight preflight WHERE preflight.ready = 1);

DELETE sub
FROM subscription sub
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%'
  AND EXISTS (SELECT 1 FROM tmp_dodam_demosub_preflight preflight WHERE preflight.ready = 1);

DELETE tx
FROM `transaction` tx
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')
  AND EXISTS (SELECT 1 FROM tmp_dodam_demosub_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 4. subscriptions (registered plans). ACTIVE -> next charge in October (September is already charged below), PAUSED -> NULL.
-- ---------------------------------------------------------------------------
INSERT INTO subscription (
  subscription_id, user_id, card_id, name, payment_amount, billing_cycle, payment_day,
  next_payment_date, end_date, status, category, created_at, updated_at, canceled_at
)
SELECT
  CONCAT('SUB-', ctx.synthetic_user_code, '-', plan.sub_no),
  ctx.user_id,
  ctx.primary_card_id,
  plan.name,
  plan.payment_amount,
  'MONTHLY',
  plan.payment_day,
  CASE WHEN plan.status = 'ACTIVE' THEN DATE_ADD('2026-10-01', INTERVAL plan.payment_day - 1 DAY) ELSE NULL END,
  NULL,
  plan.status,
  plan.category,
  '2026-03-01 09:00:00',
  '2026-09-20 09:00:00',
  NULL
FROM tmp_dodam_demosub_plan plan
JOIN tmp_dodam_demosub_context ctx ON ctx.sequence_no = plan.sequence_no
WHERE plan.registered = 1
  AND EXISTS (SELECT 1 FROM tmp_dodam_demosub_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 5. SUB transactions on the primary card, one per plan per month up to last_month_no (492 4c / 625 4c shape).
--    ids: SEED-U0003-202603-SUB-01 (Mar~Aug) / SEED-SEP-U0003-202609-SUB-01 (Sep) so 6번·11번 delete patterns treat them as their own.
-- ---------------------------------------------------------------------------
INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT(m.id_prefix, ctx.synthetic_user_code, '-', m.ym, '-SUB-', plan.sub_no),
  ctx.user_id, NULL, ctx.primary_card_id, cat.category_id,
  DATE_ADD(m.month_start, INTERVAL plan.payment_day - 1 DAY), '00:10:00', 'EXPENSE', plan.payment_amount, NULL,
  plan.name, 'CARD', 'FIXED', '구독 결제', NULL,
  0, 'SEED', '2026-09-20 09:00:00', NULL
FROM tmp_dodam_demosub_plan plan
JOIN tmp_dodam_demosub_context ctx ON ctx.sequence_no = plan.sequence_no
JOIN tmp_dodam_demosub_months m ON m.month_no <= plan.last_month_no
JOIN tmp_dodam_demosub_categories cat ON cat.code = 'SUBSCRIPTION'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_demosub_preflight preflight WHERE preflight.ready = 1);

-- ---------------------------------------------------------------------------
-- 6. payment history: one SUCCESS per SUB transaction of a registered plan (same date, same amount)
-- ---------------------------------------------------------------------------
INSERT INTO subscription_payment_history (
  subscription_id, payment_date, payment_amount, result_code, retry_count, result_message
)
SELECT
  CONCAT('SUB-', ctx.synthetic_user_code, '-', plan.sub_no),
  DATE_ADD(m.month_start, INTERVAL plan.payment_day - 1 DAY),
  plan.payment_amount,
  'SUCCESS',
  0,
  '정상 결제'
FROM tmp_dodam_demosub_plan plan
JOIN tmp_dodam_demosub_context ctx ON ctx.sequence_no = plan.sequence_no
JOIN tmp_dodam_demosub_months m ON m.month_no <= plan.last_month_no
WHERE plan.registered = 1
  AND EXISTS (SELECT 1 FROM tmp_dodam_demosub_preflight preflight WHERE preflight.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 7. verification
-- ---------------------------------------------------------------------------
-- Expected preflight row: ready 1, 4 / 4 / 1 / 24 / 4 / 4.
SELECT * FROM tmp_dodam_demosub_preflight;

-- Expected: U0001 5 (ACTIVE 5, 54,590) / U0002 6 (6, 83,680) / U0003 7 (7, 94,490) / U0004 5 (ACTIVE 4, 37,690; PAUSED 1).
SELECT
  ctx.synthetic_user_code,
  COUNT(*) AS subscription_count,
  SUM(sub.status = 'ACTIVE') AS active_count,
  SUM(CASE WHEN sub.status = 'ACTIVE' THEN sub.payment_amount ELSE 0 END) AS active_monthly_total,
  MIN(sub.next_payment_date) AS nearest_next_payment_date
FROM subscription sub
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%'
GROUP BY ctx.synthetic_user_code
ORDER BY ctx.synthetic_user_code;

-- Expected: 165 SUB transactions (158 registered + 7 디즈니+), 158 history rows, first 2026-03-03, last 2026-09-27.
SELECT
  (SELECT COUNT(*) FROM `transaction` tx
     JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
    WHERE tx.source_type = 'SEED'
      AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')) AS sub_transaction_count,
  (SELECT COUNT(*) FROM subscription_payment_history history
     JOIN subscription sub ON sub.subscription_id = history.subscription_id
     JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = sub.user_id
    WHERE sub.subscription_id LIKE 'SUB-U%') AS history_count,
  (SELECT MIN(tx.transaction_date) FROM `transaction` tx
     JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
    WHERE tx.source_type = 'SEED'
      AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')) AS first_date,
  (SELECT MAX(tx.transaction_date) FROM `transaction` tx
     JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
    WHERE tx.source_type = 'SEED'
      AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')) AS last_date;

-- Expected: 0 — every history row has a SUB transaction on the same date with the same amount (492-style and 625-style ids both count).
SELECT COUNT(*) AS history_without_transaction_count
FROM subscription_payment_history history
JOIN subscription sub ON sub.subscription_id = history.subscription_id
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = sub.user_id
LEFT JOIN `transaction` tx
  ON tx.user_id = sub.user_id
 AND tx.transaction_date = history.payment_date
 AND tx.amount = history.payment_amount
 AND tx.merchant_name = sub.name
 AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')
WHERE sub.subscription_id LIKE 'SUB-U%' AND tx.transaction_id IS NULL;

-- Expected: 0 (card belongs to the user, September rows inside 09-01 ~ 09-27, category set, CARD payment with card_id and no account_id).
SELECT COUNT(*) AS contract_violation_count
FROM `transaction` tx
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
LEFT JOIN `card` c ON c.card_id = tx.card_id AND c.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')
  AND (tx.amount <= 0
       OR tx.category_id IS NULL
       OR tx.account_id IS NOT NULL
       OR tx.card_id IS NULL
       OR c.card_id IS NULL
       OR tx.payment_method <> 'CARD'
       OR (tx.transaction_id LIKE 'SEED-SEP-%' AND tx.transaction_date NOT BETWEEN '2026-09-01' AND '2026-09-27')
       OR (tx.transaction_id NOT LIKE 'SEED-SEP-%' AND tx.transaction_date NOT BETWEEN '2026-03-01' AND '2026-08-31'));

-- Expected: 0 — no subscription of the four users has a card of someone else (V30 composite FK would have refused it anyway).
SELECT COUNT(*) AS card_mismatch_count
FROM subscription sub
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = sub.user_id
LEFT JOIN `card` card_row ON card_row.card_id = sub.card_id AND card_row.user_id = sub.user_id
WHERE sub.subscription_id LIKE 'SUB-U%' AND card_row.card_id IS NULL;

-- Expected: 5,994 — the other seed users keep their 8번 subscriptions (6,000 - 6 rows of U0001~U0004).
SELECT COUNT(*) AS untouched_other_user_subscription_count
FROM subscription sub
WHERE sub.subscription_id LIKE 'SUB-U%'
  AND sub.user_id NOT IN (SELECT user_id FROM tmp_dodam_demosub_context);

-- Expected: one row, 디즈니+ 9,900 x 7 (2026-03-27 ~ 2026-09-27) — what GET /subscriptions/candidates should return for U0003.
SELECT tx.merchant_name, tx.amount, COUNT(*) AS occurrences, MIN(tx.transaction_date) AS first_date, MAX(tx.transaction_date) AS last_date
FROM `transaction` tx
JOIN tmp_dodam_demosub_context ctx ON ctx.user_id = tx.user_id
LEFT JOIN subscription sub ON sub.user_id = tx.user_id AND sub.name = tx.merchant_name AND sub.status <> 'CANCELED'
WHERE tx.source_type = 'SEED'
  AND (tx.transaction_id LIKE 'SEED-U%-SUB-%' OR tx.transaction_id LIKE 'SEED-SEP-U%-SUB-%')
  AND sub.subscription_id IS NULL
GROUP BY ctx.synthetic_user_code, tx.merchant_name, tx.amount;

-- ---------------------------------------------------------------------------
-- cleanup: scratch tables (regular tables; see header)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_demosub_categories;
DROP TABLE IF EXISTS tmp_dodam_demosub_context;
DROP TABLE IF EXISTS tmp_dodam_demosub_months;
DROP TABLE IF EXISTS tmp_dodam_demosub_plan;
DROP TABLE IF EXISTS tmp_dodam_demosub_preflight;
