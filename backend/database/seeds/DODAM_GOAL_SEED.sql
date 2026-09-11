-- DODAM Goal, SavingGoal, SpendingGoal, GoalAccount, GoalHistory seed
-- Jira: S15P21D105-493
-- Dialect: MySQL 8.x
-- Schema basis: docs/ERD/DODAM_ERDCLOUD_IMPORT.sql (revision 2026-09-07),
--               migrations V5 (goal), V7 (saving_goal, spending_goal), V8 (goal_account), V9 (goal_history)
-- Dependency: S15P21D105-491 User/Account seed, S15P21D105-524 Deposit/Savings seed (DEPOSIT for odd users,
--             SAVINGS for even users). DODAM_TRANSACTION_SEED.sql is not required but makes 510 spending-progress meaningful.
-- Dataset: 4,000 saving goals (1 per user), 2,000 spending goals (SPENDING_FOCUSED + VOLATILE cohorts),
--          about 5,000 goal_account links, 28,000 saving-goal history rows + 2,000 spending-goal history rows
-- Snapshot: 2026-09-07
--
-- Rules (must match the Goal API, backend/docs/goal.md)
-- ------------------------------------------------------
-- - goal.type SAVING has a saving_goal row (target_amount, target_date); SPENDING has a spending_goal row (monthly_budget).
--   goal_type in the child row always equals the parent type (composite FK).
-- - goal_account links only SAVING goals to DEMAND_DEPOSIT / DEPOSIT / SAVINGS accounts of the same user; one account belongs
--   to at most one goal (uk_goal_account_account). contribution_amount stays NULL (whole balance counts).
-- - goal.current_amount = SUM(balance) of the linked accounts, exactly what GoalAccountService / GoalCalculationService recompute.
-- - goal_history: GOAL_CREATED on 2026-03-01 (0 won) + AMOUNT_UPDATED at each month end 2026-03-31 .. 2026-08-31 with the amount
--   growing linearly to the current linked balance; progress_rate = amount / target * 100 (4 decimals, capped 100).
--   forecast_date is left NULL (the API recomputes it from the live pace).
-- - Seed goals are recognised by created_at = '2026-03-01 09:00:00' for the K-Means users. Rerunning deletes and regenerates them.
--
-- Cohort mapping (MOD(seq-1,4): 0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE)
--   saving goal:   everyone. odd users link their DEPOSIT account, even users their SAVINGS account;
--                  BALANCED users additionally link the primary demand-deposit account.
--                  target = monthly_income x (6 + MOD(seq,7)) rounded to 100,000; target_date = 2027-03-01 + MOD(seq,12) months
--                  status: PAUSED for every 40th user, otherwise ACTIVE
--   spending goal: SPENDING_FOCUSED and VOLATILE users. monthly_budget = monthly_income x 45% rounded to 10,000
--                  (below their variable spending, so goal-comparison / risk show WARNING or EXCEEDED for demo)

SET NAMES utf8mb4;
SET time_zone = '+09:00';

DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_digit;
CREATE TEMPORARY TABLE tmp_dodam_493_digit (digit TINYINT NOT NULL, PRIMARY KEY (digit));
INSERT INTO tmp_dodam_493_digit (digit) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_months;
CREATE TEMPORARY TABLE tmp_dodam_493_months (month_no TINYINT NOT NULL, month_end DATE NOT NULL, PRIMARY KEY (month_no));
INSERT INTO tmp_dodam_493_months (month_no, month_end)
SELECT d.digit + 1, LAST_DAY(DATE_ADD('2026-03-01', INTERVAL d.digit MONTH))
FROM tmp_dodam_493_digit d WHERE d.digit BETWEEN 0 AND 5;

-- ---------------------------------------------------------------------------
-- 1. users and the accounts each goal will link
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_context;
CREATE TEMPORARY TABLE tmp_dodam_493_context (
  sequence_no INT NOT NULL,
  synthetic_user_code CHAR(5) NOT NULL,
  behavior_index TINYINT NOT NULL,
  user_id BIGINT NOT NULL,
  monthly_income BIGINT NOT NULL,
  primary_account_id BIGINT NOT NULL,
  saving_account_id BIGINT NULL,
  saving_account_type VARCHAR(30) NULL,
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_tmp_dodam_493_context_user (user_id)
);
INSERT INTO tmp_dodam_493_context (
  sequence_no, synthetic_user_code, behavior_index, user_id, monthly_income, primary_account_id,
  saving_account_id, saving_account_type
)
SELECT
  numbers.sequence_no,
  CONCAT('U', LPAD(numbers.sequence_no, 4, '0')),
  MOD(numbers.sequence_no - 1, 4),
  user_row.user_id,
  COALESCE(profile_row.monthly_income, 2300000),
  primary_row.account_id,
  saving_row.account_id,
  saving_row.account_type
FROM (
  SELECT thousands.digit * 1000 + hundreds.digit * 100 + tens.digit * 10 + ones.digit + 1 AS sequence_no
  FROM tmp_dodam_493_digit thousands
  CROSS JOIN tmp_dodam_493_digit hundreds
  CROSS JOIN tmp_dodam_493_digit tens
  CROSS JOIN tmp_dodam_493_digit ones
) numbers
JOIN `user` user_row
  ON user_row.email = CONCAT('seed.kmeans.u', LPAD(numbers.sequence_no, 4, '0'), '@dodam.invalid')
LEFT JOIN financial_profile profile_row ON profile_row.user_id = user_row.user_id
JOIN `account` primary_row
  ON primary_row.user_id = user_row.user_id
 AND primary_row.account_type = 'DEMAND_DEPOSIT'
 AND primary_row.source_type = 'SEED'
 AND primary_row.account_name = '주거래 입출금'
-- odd users: DEPOSIT (524), even users: SAVINGS (524). Exactly one row per user in the 524 dataset; MIN() guards reruns.
LEFT JOIN (
  SELECT a.user_id, MIN(a.account_id) AS account_id, MIN(a.account_type) AS account_type
  FROM `account` a
  WHERE a.source_type = 'SEED'
    AND a.status <> 'CLOSED'
    AND a.account_type IN ('DEPOSIT', 'SAVINGS')
  GROUP BY a.user_id
) saving_row ON saving_row.user_id = user_row.user_id
WHERE numbers.sequence_no BETWEEN 1 AND 4000;

-- expected goals
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_expected_goals;
CREATE TEMPORARY TABLE tmp_dodam_493_expected_goals (
  seed_goal_code VARCHAR(50) NOT NULL,
  sequence_no INT NOT NULL,
  goal_type VARCHAR(20) NOT NULL,
  name VARCHAR(150) NOT NULL,
  status VARCHAR(30) NOT NULL,
  target_amount BIGINT NULL,
  target_date DATE NULL,
  monthly_budget BIGINT NULL,
  PRIMARY KEY (seed_goal_code),
  UNIQUE KEY uk_tmp_dodam_493_goal_user_type (sequence_no, goal_type)
);
INSERT INTO tmp_dodam_493_expected_goals (
  seed_goal_code, sequence_no, goal_type, name, status, target_amount, target_date, monthly_budget
)
SELECT
  CONCAT(c.synthetic_user_code, '_SAVING'),
  c.sequence_no,
  'SAVING',
  CASE MOD(c.sequence_no, 5)
    WHEN 0 THEN '비상금 만들기'
    WHEN 1 THEN '여행 자금'
    WHEN 2 THEN '전세 보증금'
    WHEN 3 THEN '노트북 바꾸기'
    ELSE '내 집 마련 첫걸음'
  END,
  CASE WHEN MOD(c.sequence_no, 40) = 0 THEN 'PAUSED' ELSE 'ACTIVE' END,
  ROUND(c.monthly_income * (6 + MOD(c.sequence_no, 7)), -5),
  DATE_ADD('2027-03-01', INTERVAL MOD(c.sequence_no, 12) MONTH),
  NULL
FROM tmp_dodam_493_context c
UNION ALL
SELECT
  CONCAT(c.synthetic_user_code, '_SPENDING'),
  c.sequence_no,
  'SPENDING',
  '월 소비 줄이기',
  'ACTIVE',
  NULL,
  NULL,
  ROUND(c.monthly_income * 0.45, -4)
FROM tmp_dodam_493_context c
WHERE c.behavior_index IN (1, 3);

-- expected links: saving goal -> deposit/savings account (+ primary for BALANCED)
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_expected_links;
CREATE TEMPORARY TABLE tmp_dodam_493_expected_links (
  seed_goal_code VARCHAR(50) NOT NULL,
  account_id BIGINT NOT NULL,
  account_type VARCHAR(30) NOT NULL,
  PRIMARY KEY (seed_goal_code, account_id),
  UNIQUE KEY uk_tmp_dodam_493_link_account (account_id)
);
INSERT INTO tmp_dodam_493_expected_links (seed_goal_code, account_id, account_type)
SELECT CONCAT(c.synthetic_user_code, '_SAVING'), c.saving_account_id, c.saving_account_type
FROM tmp_dodam_493_context c
WHERE c.saving_account_id IS NOT NULL
UNION ALL
SELECT CONCAT(c.synthetic_user_code, '_SAVING'), c.primary_account_id, 'DEMAND_DEPOSIT'
FROM tmp_dodam_493_context c
WHERE c.behavior_index = 0;

-- ---------------------------------------------------------------------------
-- 2. preflight
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_preflight;
CREATE TEMPORARY TABLE tmp_dodam_493_preflight (
  ready TINYINT(1) NOT NULL,
  user_count INT NOT NULL,
  users_with_saving_account_count INT NOT NULL,
  expected_goal_count INT NOT NULL,
  linked_account_conflict_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (user_count = 4000),
  CHECK (users_with_saving_account_count = 4000),
  CHECK (expected_goal_count = 6000),
  CHECK (linked_account_conflict_count = 0)
);
-- linked_account_conflict_count: accounts we want to link that already belong to a live goal that is NOT a seed goal.
INSERT INTO tmp_dodam_493_preflight (
  ready, user_count, users_with_saving_account_count, expected_goal_count, linked_account_conflict_count
)
SELECT
  CASE WHEN checks.user_count = 4000
        AND checks.users_with_saving_account_count = 4000
        AND checks.expected_goal_count = 6000
        AND checks.linked_account_conflict_count = 0
       THEN 1 ELSE 0 END,
  checks.user_count, checks.users_with_saving_account_count, checks.expected_goal_count, checks.linked_account_conflict_count
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_493_context) AS user_count,
    (SELECT COUNT(*) FROM tmp_dodam_493_context WHERE saving_account_id IS NOT NULL) AS users_with_saving_account_count,
    (SELECT COUNT(*) FROM tmp_dodam_493_expected_goals) AS expected_goal_count,
    (SELECT COUNT(*)
       FROM tmp_dodam_493_expected_links link
       JOIN goal_account ga ON ga.account_id = link.account_id
       JOIN goal g ON g.goal_id = ga.goal_id
      WHERE g.created_at <> '2026-03-01 09:00:00') AS linked_account_conflict_count
) checks;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 3. remove the previous run (children first). Seed goals: K-Means users + created_at marker.
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_old_goals;
CREATE TEMPORARY TABLE tmp_dodam_493_old_goals (goal_id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO tmp_dodam_493_old_goals (goal_id)
SELECT g.goal_id
FROM goal g
JOIN tmp_dodam_493_context ctx ON ctx.user_id = g.user_id
WHERE g.created_at = '2026-03-01 09:00:00'
  AND EXISTS (SELECT 1 FROM tmp_dodam_493_preflight preflight WHERE preflight.ready = 1);

DELETE h FROM goal_history h JOIN tmp_dodam_493_old_goals old ON old.goal_id = h.goal_id;
DELETE ga FROM goal_account ga JOIN tmp_dodam_493_old_goals old ON old.goal_id = ga.goal_id;
DELETE sg FROM saving_goal sg JOIN tmp_dodam_493_old_goals old ON old.goal_id = sg.goal_id;
DELETE pg FROM spending_goal pg JOIN tmp_dodam_493_old_goals old ON old.goal_id = pg.goal_id;
DELETE g FROM goal g JOIN tmp_dodam_493_old_goals old ON old.goal_id = g.goal_id;

-- ---------------------------------------------------------------------------
-- 4. goal (current_amount is filled after the links exist)
-- ---------------------------------------------------------------------------
INSERT INTO goal (user_id, name, type, status, current_amount, created_at, updated_at, deleted_at)
SELECT
  ctx.user_id, expected.name, expected.goal_type, expected.status, 0,
  '2026-03-01 09:00:00', '2026-09-07 09:00:00', NULL
FROM tmp_dodam_493_expected_goals expected
JOIN tmp_dodam_493_context ctx ON ctx.sequence_no = expected.sequence_no
WHERE EXISTS (SELECT 1 FROM tmp_dodam_493_preflight preflight WHERE preflight.ready = 1);

-- resolve the new goal ids back to the seed codes (user + type is unique inside this seed)
DROP TEMPORARY TABLE IF EXISTS tmp_dodam_493_goal_ids;
CREATE TEMPORARY TABLE tmp_dodam_493_goal_ids (
  seed_goal_code VARCHAR(50) NOT NULL PRIMARY KEY,
  goal_id BIGINT NOT NULL,
  UNIQUE KEY uk_tmp_dodam_493_goal_id (goal_id)
);
INSERT INTO tmp_dodam_493_goal_ids (seed_goal_code, goal_id)
SELECT expected.seed_goal_code, g.goal_id
FROM tmp_dodam_493_expected_goals expected
JOIN tmp_dodam_493_context ctx ON ctx.sequence_no = expected.sequence_no
JOIN goal g
  ON g.user_id = ctx.user_id
 AND g.type = expected.goal_type
 AND g.created_at = '2026-03-01 09:00:00'
 AND g.deleted_at IS NULL;

INSERT INTO saving_goal (goal_id, goal_type, target_amount, target_date)
SELECT ids.goal_id, 'SAVING', expected.target_amount, expected.target_date
FROM tmp_dodam_493_expected_goals expected
JOIN tmp_dodam_493_goal_ids ids ON ids.seed_goal_code = expected.seed_goal_code
WHERE expected.goal_type = 'SAVING';

INSERT INTO spending_goal (goal_id, goal_type, monthly_budget)
SELECT ids.goal_id, 'SPENDING', expected.monthly_budget
FROM tmp_dodam_493_expected_goals expected
JOIN tmp_dodam_493_goal_ids ids ON ids.seed_goal_code = expected.seed_goal_code
WHERE expected.goal_type = 'SPENDING';

-- ---------------------------------------------------------------------------
-- 5. goal_account
-- ---------------------------------------------------------------------------
INSERT INTO goal_account (user_id, goal_id, account_id, account_type, contribution_amount)
SELECT ctx.user_id, ids.goal_id, link.account_id, link.account_type, NULL
FROM tmp_dodam_493_expected_links link
JOIN tmp_dodam_493_goal_ids ids ON ids.seed_goal_code = link.seed_goal_code
JOIN tmp_dodam_493_expected_goals expected ON expected.seed_goal_code = link.seed_goal_code
JOIN tmp_dodam_493_context ctx ON ctx.sequence_no = expected.sequence_no;

-- current_amount = linked balances (same rule as GoalAccountService)
UPDATE goal g
JOIN (
  SELECT ga.goal_id, SUM(a.balance) AS linked_balance
  FROM goal_account ga
  JOIN `account` a ON a.account_id = ga.account_id
  JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = ga.goal_id
  GROUP BY ga.goal_id
) linked ON linked.goal_id = g.goal_id
SET g.current_amount = GREATEST(0, linked.linked_balance);

-- ---------------------------------------------------------------------------
-- 6. goal_history: creation snapshot + month-end snapshots growing linearly to the current amount
-- ---------------------------------------------------------------------------
INSERT INTO goal_history (goal_id, recorded_date, current_amount, progress_rate, forecast_date, event, created_at)
SELECT
  ids.goal_id, '2026-03-01', 0,
  CASE WHEN expected.goal_type = 'SAVING' THEN 0.0000 ELSE NULL END,
  NULL, 'GOAL_CREATED', '2026-03-01 09:00:00'
FROM tmp_dodam_493_expected_goals expected
JOIN tmp_dodam_493_goal_ids ids ON ids.seed_goal_code = expected.seed_goal_code;

INSERT INTO goal_history (goal_id, recorded_date, current_amount, progress_rate, forecast_date, event, created_at)
SELECT
  ids.goal_id,
  m.month_end,
  FLOOR(g.current_amount * m.month_no / 6),
  LEAST(100.0000, ROUND(FLOOR(g.current_amount * m.month_no / 6) * 100.0 / expected.target_amount, 4)),
  NULL,
  'AMOUNT_UPDATED',
  TIMESTAMP(m.month_end, '23:00:00')
FROM tmp_dodam_493_expected_goals expected
JOIN tmp_dodam_493_goal_ids ids ON ids.seed_goal_code = expected.seed_goal_code
JOIN goal g ON g.goal_id = ids.goal_id
CROSS JOIN tmp_dodam_493_months m
WHERE expected.goal_type = 'SAVING';

COMMIT;

-- ---------------------------------------------------------------------------
-- 7. verification
-- ---------------------------------------------------------------------------
-- Expected: SAVING 4000 (3900 ACTIVE / 100 PAUSED), SPENDING 2000 ACTIVE.
SELECT g.type, g.status, COUNT(*) AS goal_count
FROM goal g JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = g.goal_id
GROUP BY g.type, g.status ORDER BY g.type, g.status;

-- Expected: 4000 saving_goal, 2000 spending_goal, 5000 goal_account (4000 + 1000 BALANCED primaries), 4000*7 + 2000 = 30000 history rows.
SELECT
  (SELECT COUNT(*) FROM saving_goal sg JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = sg.goal_id) AS saving_goal_count,
  (SELECT COUNT(*) FROM spending_goal pg JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = pg.goal_id) AS spending_goal_count,
  (SELECT COUNT(*) FROM goal_account ga JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = ga.goal_id) AS goal_account_count,
  (SELECT COUNT(*) FROM goal_history h JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = h.goal_id) AS goal_history_count;

-- Expected: 0 — current_amount equals the linked balance sum, every link points to a SAVING goal of the same user,
--           and progress_rate stays within 0..100.
SELECT
  (SELECT COUNT(*)
     FROM goal g
     JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = g.goal_id
     LEFT JOIN (SELECT ga.goal_id, SUM(a.balance) AS s FROM goal_account ga JOIN `account` a ON a.account_id = ga.account_id GROUP BY ga.goal_id) linked
       ON linked.goal_id = g.goal_id
    WHERE g.current_amount <> COALESCE(GREATEST(0, linked.s), 0)) AS current_amount_mismatch_count,
  (SELECT COUNT(*)
     FROM goal_account ga
     JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = ga.goal_id
     JOIN goal g ON g.goal_id = ga.goal_id
     JOIN `account` a ON a.account_id = ga.account_id
    WHERE g.type <> 'SAVING' OR a.user_id <> g.user_id OR a.account_type <> ga.account_type
       OR a.account_type NOT IN ('DEMAND_DEPOSIT', 'DEPOSIT', 'SAVINGS')) AS link_contract_violation_count,
  (SELECT COUNT(*)
     FROM goal_history h
     JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = h.goal_id
    WHERE h.progress_rate IS NOT NULL AND (h.progress_rate < 0 OR h.progress_rate > 100)) AS progress_rate_violation_count;

-- Demo users: one row per goal for a quick look.
SELECT u.email, g.goal_id, g.type, g.status, g.current_amount, sg.target_amount, sg.target_date, pg.monthly_budget
FROM goal g
JOIN tmp_dodam_493_goal_ids ids ON ids.goal_id = g.goal_id
JOIN `user` u ON u.user_id = g.user_id
LEFT JOIN saving_goal sg ON sg.goal_id = g.goal_id
LEFT JOIN spending_goal pg ON pg.goal_id = g.goal_id
WHERE u.email IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                  'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid')
ORDER BY u.email, g.type;
