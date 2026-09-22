-- U0003 derived report/pattern verification (read-only)
-- Jira: S15P21D105-699 (리포트·패턴 분석 파생 결과 정합성 복구)
-- Dialect: MySQL 8.x
--
-- Purpose
-- -------
-- Compare the final 679 ledger aggregates with the snapshots shown by the monthly report and pattern analysis.
-- Run this file before reset, after force-refresh APIs, and after the monthly report is regenerated.
-- This file never writes permanent data and does not print sensitive account/card numbers or external user keys.
--
-- Expected source values after the revised 678 + 679 seeds:
-- - August income 3,012,400 / spending 2,559,221 / saving transfer 400,000 / fixed spending 1,565,221
-- - March~August pattern window: spendingToIncomeRatio about 0.86, savingRate about 0.13,
--   fixedExpenseShare about 0.585 (exact response values are rounded by the application).

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- Q1. The row must be exactly 1. `profile_contract_ready = 1` means the final 679 profile is present.
SELECT
  u.user_id,
  u.email,
  fp.monthly_income,
  fp.fixed_expense,
  CASE WHEN fp.monthly_income = 3012400 AND fp.fixed_expense = 1150000 THEN 1 ELSE 0 END AS profile_contract_ready
FROM `user` u
JOIN financial_profile fp ON fp.user_id = u.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND u.deleted_at IS NULL;

-- Q2. Monthly source aggregates. Spending excludes NON_SPENDING; saving is NON_SPENDING EXPENSE.
SELECT
  DATE_FORMAT(tx.transaction_date, '%Y-%m') AS month_key,
  SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END) AS income,
  SUM(CASE WHEN tx.transaction_type = 'EXPENSE'
            AND (tx.spending_type IS NULL OR tx.spending_type <> 'NON_SPENDING')
           THEN tx.amount ELSE 0 END) AS spending,
  SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'NON_SPENDING'
           THEN tx.amount ELSE 0 END) AS saving_transfer,
  SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'FIXED'
           THEN tx.amount ELSE 0 END) AS fixed_spending,
  ROUND(
    SUM(CASE WHEN tx.transaction_type = 'EXPENSE'
              AND (tx.spending_type IS NULL OR tx.spending_type <> 'NON_SPENDING')
             THEN tx.amount ELSE 0 END)
      / NULLIF(SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END), 0),
    4
  ) AS spending_to_income_ratio,
  ROUND(
    SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'NON_SPENDING'
             THEN tx.amount ELSE 0 END)
      / NULLIF(SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END), 0),
    4
  ) AS saving_rate,
  ROUND(
    SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'FIXED'
             THEN tx.amount ELSE 0 END)
      / NULLIF(SUM(CASE WHEN tx.transaction_type = 'EXPENSE'
                         AND (tx.spending_type IS NULL OR tx.spending_type <> 'NON_SPENDING')
                        THEN tx.amount ELSE 0 END), 0),
    4
  ) AS fixed_expense_share,
  COUNT(*) AS transaction_count
FROM `transaction` tx
JOIN `user` u ON u.user_id = tx.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND u.deleted_at IS NULL
  AND tx.hidden = FALSE
  AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-08-31'
GROUP BY DATE_FORMAT(tx.transaction_date, '%Y-%m')
ORDER BY month_key;

-- Q3. The completed six-month window as one ratio. This is the source for pattern userValue.
SELECT
  ROUND(spending / NULLIF(income, 0), 4) AS spending_to_income_ratio,
  ROUND(saving_transfer / NULLIF(income, 0), 4) AS saving_rate,
  ROUND(fixed_spending / NULLIF(spending, 0), 4) AS fixed_expense_share,
  spending,
  income,
  saving_transfer,
  fixed_spending
FROM (
  SELECT
    SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END) AS income,
    SUM(CASE WHEN tx.transaction_type = 'EXPENSE'
              AND (tx.spending_type IS NULL OR tx.spending_type <> 'NON_SPENDING')
             THEN tx.amount ELSE 0 END) AS spending,
    SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'NON_SPENDING'
             THEN tx.amount ELSE 0 END) AS saving_transfer,
    SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'FIXED'
             THEN tx.amount ELSE 0 END) AS fixed_spending
  FROM `transaction` tx
  JOIN `user` u ON u.user_id = tx.user_id
  WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
    AND u.deleted_at IS NULL
    AND tx.hidden = FALSE
    AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-08-31'
) source_totals;

-- Q4. Stored monthly report snapshots. No row after reset is expected until GET /reports/monthly regenerates it.
SELECT
  r.report_id,
  r.period,
  r.created_at,
  mr.data_status,
  JSON_UNQUOTE(JSON_EXTRACT(mr.efficiency_summary, '$.calculationVersion')) AS calculation_version,
  JSON_EXTRACT(mr.efficiency_summary, '$.score') AS efficiency_score,
  JSON_EXTRACT(mr.efficiency_summary, '$.dimensions') AS efficiency_dimensions,
  mr.efficiency_summary
FROM report r
JOIN monthly_report mr ON mr.report_id = r.report_id AND mr.report_type = r.type
JOIN `user` u ON u.user_id = r.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND r.type = 'MONTHLY'
  AND r.period BETWEEN '2026-03' AND '2026-08'
ORDER BY r.period;

-- Q5. Latest stored pattern result. After forceRefresh it must use window 2026-03~2026-08 and final source ratios.
SELECT
  pa.analysis_id,
  pa.status,
  pa.analyzed_at,
  pa.lookback_months,
  pa.cluster_id,
  pa.cluster_label,
  pa.fixed_expense_ratio,
  JSON_UNQUOTE(JSON_EXTRACT(pa.feature_summary_json, '$.windowStart')) AS window_start,
  JSON_UNQUOTE(JSON_EXTRACT(pa.feature_summary_json, '$.windowEnd')) AS window_end,
  JSON_EXTRACT(pa.feature_summary_json, '$.clusterFeatures.spendingToIncomeRatio') AS spending_to_income_ratio,
  JSON_EXTRACT(pa.feature_summary_json, '$.clusterFeatures.savingRate') AS saving_rate,
  JSON_EXTRACT(pa.feature_summary_json, '$.clusterFeatures.fixedExpenseShare') AS fixed_expense_share,
  pa.feature_summary_json,
  pa.peer_stats_json
FROM pattern_analysis pa
JOIN `user` u ON u.user_id = pa.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND pa.status = 'COMPLETED'
ORDER BY pa.analyzed_at DESC, pa.analysis_id DESC
LIMIT 1;
