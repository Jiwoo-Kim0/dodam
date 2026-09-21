-- Verification queries for DODAM_DEMO_DEPOSIT_SAVING_678_SEED.sql (S15P21D105-678)
-- Dialect: MySQL 8.x. Read-only. Needs no fixture or scratch table, so it can run before the first load (baseline),
-- after the load, and after a rerun; compare the three outputs.
--
-- Identification of the 678 rows (no plaintext, no hash): U0003's SAVINGS accounts with source_type SEED and
-- created_at '2026-09-21 09:00:00', and U0003's SAVING goal with created_at '2021-09-21 09:00:00' (five years before the
-- snapshot so the app's saving pace = current / elapsed months is realistic).
--
-- Expected after load (and unchanged after a rerun):
--   Q1  3 rows: 5,500,000 / 12,100,000 / 78,000,000, paid_count 11 / 121 / 78, paid_sum = balance,
--       scheduled_count 0 / 1 / 0, future_paid_count 0, withdrawal_owner_ok 1, product_ok 1
--   Q2  1 row: current_amount 95,600,000, target 150,000,000, linked_count 3, progress 63.7333, history_count 61
--       (GOAL_CREATED 2021-09-21 24,100,000 + month-end AMOUNT_UPDATED 60 rows, last 2026-08-31 94,000,000)
--   Q3  account_ids / goal_id identical between the first load and the rerun; payment_checksum identical
--   Q4  every count equals the baseline (rows of other users and U0003's non-678 rows are untouched)
--   Q5  0 / 0
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- Q1. the three accounts
SELECT
  a.account_id, a.account_name, a.account_no_masked, a.bank_code, a.status, a.balance,
  d.interest_rate, d.monthly_amount, d.payment_day, d.next_payment_date, d.opening_date, d.expiry_date,
  d.withdrawal_account_id, d.financial_product_id,
  (SELECT COUNT(*) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'PAID') AS paid_count,
  (SELECT COALESCE(SUM(p.amount), 0) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'PAID') AS paid_sum,
  (SELECT COUNT(*) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'SCHEDULED') AS scheduled_count,
  (SELECT COUNT(*) FROM savings_payment p WHERE p.deposit_saving_id = a.account_id AND p.status = 'PAID' AND p.payment_date > '2026-09-21') AS future_paid_count,
  (SELECT COUNT(*) FROM `account` w WHERE w.account_id = d.withdrawal_account_id AND w.user_id = a.user_id
      AND w.account_type = 'DEMAND_DEPOSIT' AND w.status = 'ACTIVE') AS withdrawal_owner_ok,
  CASE
    WHEN a.account_name = '신한 청년도약계좌' THEN (SELECT COUNT(*) FROM financial_product p
        JOIN financial_company c ON c.company_id = p.company_id
       WHERE p.product_id = d.financial_product_id AND c.company_code = 'SHINHAN_BANK'
         AND p.product_type = 'SAVINGS' AND p.product_name = '신한 청년도약계좌')
    ELSE IF(d.financial_product_id IS NULL, 1, 0)
  END AS product_ok
FROM `account` a
JOIN `user` u ON u.user_id = a.user_id
LEFT JOIN deposit_saving d ON d.account_id = a.account_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND a.account_type = 'SAVINGS' AND a.source_type = 'SEED' AND a.created_at = '2026-09-21 09:00:00'
ORDER BY a.account_id;

-- Q2. the goal
SELECT
  g.goal_id, g.name, g.type, g.status, g.current_amount, sg.target_amount, sg.target_date, g.created_at, g.updated_at,
  (SELECT COUNT(*) FROM goal_account ga WHERE ga.goal_id = g.goal_id) AS linked_count,
  (SELECT COALESCE(SUM(COALESCE(ga.contribution_amount, a.balance)), 0)
     FROM goal_account ga JOIN `account` a ON a.account_id = ga.account_id WHERE ga.goal_id = g.goal_id) AS secured_amount,
  ROUND(g.current_amount * 100.0 / sg.target_amount, 4) AS progress_rate,
  (SELECT COUNT(*) FROM goal_history h WHERE h.goal_id = g.goal_id) AS history_count,
  (SELECT MIN(h.current_amount) FROM goal_history h WHERE h.goal_id = g.goal_id AND h.event = 'GOAL_CREATED') AS history_created_amount,
  (SELECT h.current_amount FROM goal_history h WHERE h.goal_id = g.goal_id ORDER BY h.recorded_date DESC, h.goal_history_id DESC LIMIT 1) AS history_last_amount
FROM goal g
JOIN `user` u ON u.user_id = g.user_id
JOIN saving_goal sg ON sg.goal_id = g.goal_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND g.type = 'SAVING' AND g.deleted_at IS NULL AND g.created_at = '2021-09-21 09:00:00';

-- Q3. identity / idempotency fingerprint (compare between first load and rerun)
SELECT
  (SELECT GROUP_CONCAT(a.account_id ORDER BY a.account_id) FROM `account` a JOIN `user` u ON u.user_id = a.user_id
    WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND a.account_type = 'SAVINGS'
      AND a.source_type = 'SEED' AND a.created_at = '2026-09-21 09:00:00') AS account_ids,
  (SELECT GROUP_CONCAT(g.goal_id) FROM goal g JOIN `user` u ON u.user_id = g.user_id
    WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND g.type = 'SAVING' AND g.deleted_at IS NULL
      AND g.created_at = '2021-09-21 09:00:00') AS goal_id,
  (SELECT SUM(p.installment_number * p.amount) + COUNT(*) + SUM(DATEDIFF(p.payment_date, '2016-01-01'))
     FROM savings_payment p JOIN `account` a ON a.account_id = p.deposit_saving_id JOIN `user` u ON u.user_id = a.user_id
    WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND a.account_type = 'SAVINGS'
      AND a.source_type = 'SEED' AND a.created_at = '2026-09-21 09:00:00') AS payment_checksum;

-- Q4. preservation counts (run before the first load as the baseline; every value must be identical afterwards)
SELECT
  (SELECT COUNT(*) FROM `account` a WHERE NOT (a.account_type = 'SAVINGS' AND a.source_type = 'SEED'
      AND a.created_at = '2026-09-21 09:00:00')) AS other_accounts,
  (SELECT COALESCE(SUM(a.balance), 0) FROM `account` a WHERE NOT (a.account_type = 'SAVINGS' AND a.source_type = 'SEED'
      AND a.created_at = '2026-09-21 09:00:00')) AS other_accounts_balance,
  (SELECT COUNT(*) FROM deposit_saving d WHERE d.created_at <> '2026-09-21 09:00:00') AS other_deposit_savings,
  (SELECT COUNT(*) FROM savings_payment p JOIN deposit_saving d ON d.account_id = p.deposit_saving_id
    WHERE d.created_at <> '2026-09-21 09:00:00') AS other_savings_payments,
  (SELECT COUNT(*) FROM goal g WHERE g.created_at <> '2021-09-21 09:00:00') AS other_goals,
  (SELECT COALESCE(SUM(g.current_amount), 0) FROM goal g WHERE g.created_at <> '2021-09-21 09:00:00') AS other_goals_current_amount,
  (SELECT COUNT(*) FROM goal_account ga JOIN goal g ON g.goal_id = ga.goal_id
    WHERE g.created_at <> '2021-09-21 09:00:00') AS other_goal_links,
  (SELECT COUNT(*) FROM goal_history h JOIN goal g ON g.goal_id = h.goal_id
    WHERE g.created_at <> '2021-09-21 09:00:00') AS other_goal_histories,
  (SELECT COUNT(*) FROM financial_product) AS products,
  (SELECT COUNT(*) FROM financial_product_option) AS product_options,
  (SELECT COUNT(*) FROM `user`) AS users;

-- Q5. isolation (must be 0 / 0)
SELECT
  (SELECT COUNT(*) FROM `account` a JOIN `user` u ON u.user_id = a.user_id
    WHERE a.account_type = 'SAVINGS' AND a.source_type = 'SEED' AND a.created_at = '2026-09-21 09:00:00'
      AND u.email <> 'seed.kmeans.u0003@dodam.invalid') AS accounts_678_on_other_users,
  (SELECT COUNT(*) FROM goal g JOIN `user` u ON u.user_id = g.user_id
    WHERE g.created_at = '2021-09-21 09:00:00' AND u.email <> 'seed.kmeans.u0003@dodam.invalid') AS goals_678_on_other_users;
