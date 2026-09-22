-- U0003 stale monthly-report reset after the revised 678 + 679 source seeds
-- Jira: S15P21D105-699 (리포트·패턴 분석 파생 결과 정합성 복구)
-- Dialect: MySQL 8.x
--
-- Scope and safety
-- ----------------
-- - Deletes only U0003 MONTHLY reports for completed source months 2026-03~2026-08.
-- - Never deletes source transactions, pattern history, forecasts, diagnoses, recommendations, or another user's rows.
-- - Refuses to select any target unless the final 679 August ledger contract is present.
-- - Rerunning is safe: the second run selects zero report rows.
-- - Run DODAM_DEMO_U0003_DERIVED_VERIFY_699.sql before and after this file.
-- - Uses regular scratch tables; do not run this file concurrently or request U0003 monthly reports while it runs.
--
-- Why pattern/forecast/diagnosis rows remain
-- ------------------------------------------
-- Those APIs preserve calculation history and support forceRefresh, which creates a new latest result. Deleting their history would
-- break auditability and recommendation foreign keys. A READY monthly report is immutable and has no refresh API, so its parent and
-- child rows must be removed before GET /reports/monthly can generate a corrected snapshot.
--
-- Expected source contract (August 2026, revised 678 + 679):
-- income 3,012,400 / spending 2,559,221 / saving 400,000 / fixed 1,565,221.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

DROP TABLE IF EXISTS tmp_dodam_699_report_targets;
DROP TABLE IF EXISTS tmp_dodam_699_preflight;
DROP TABLE IF EXISTS tmp_dodam_699_context;

CREATE TABLE tmp_dodam_699_context (
  user_id BIGINT NOT NULL PRIMARY KEY
);

INSERT INTO tmp_dodam_699_context (user_id)
SELECT u.user_id
FROM `user` u
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND u.deleted_at IS NULL;

CREATE TABLE tmp_dodam_699_preflight (
  ready TINYINT NOT NULL PRIMARY KEY,
  user_count INT NOT NULL,
  august_income BIGINT NOT NULL,
  august_spending BIGINT NOT NULL,
  august_saving BIGINT NOT NULL,
  august_fixed BIGINT NOT NULL
);

INSERT INTO tmp_dodam_699_preflight (
  ready, user_count, august_income, august_spending, august_saving, august_fixed
)
SELECT
  CASE WHEN source.user_count = 1
          AND source.august_income = 3012400
          AND source.august_spending = 2559221
          AND source.august_saving = 400000
          AND source.august_fixed = 1565221
       THEN 1 ELSE 0 END,
  source.user_count,
  source.august_income,
  source.august_spending,
  source.august_saving,
  source.august_fixed
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_699_context) AS user_count,
    COALESCE(SUM(CASE WHEN tx.transaction_type = 'INCOME' THEN tx.amount ELSE 0 END), 0) AS august_income,
    COALESCE(SUM(CASE WHEN tx.transaction_type = 'EXPENSE'
                       AND (tx.spending_type IS NULL OR tx.spending_type <> 'NON_SPENDING')
                      THEN tx.amount ELSE 0 END), 0) AS august_spending,
    COALESCE(SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'NON_SPENDING'
                      THEN tx.amount ELSE 0 END), 0) AS august_saving,
    COALESCE(SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.spending_type = 'FIXED'
                      THEN tx.amount ELSE 0 END), 0) AS august_fixed
  FROM tmp_dodam_699_context ctx
  LEFT JOIN `transaction` tx ON tx.user_id = ctx.user_id
    AND tx.hidden = FALSE
    AND tx.transaction_date BETWEEN '2026-08-01' AND '2026-08-31'
) source;

-- `ready = 0` means nothing below is selected or deleted. Stop and apply the final 678 + 679 seeds first.
SELECT * FROM tmp_dodam_699_preflight;

CREATE TABLE tmp_dodam_699_report_targets (
  report_id BIGINT NOT NULL PRIMARY KEY,
  period VARCHAR(20) NOT NULL
);

INSERT INTO tmp_dodam_699_report_targets (report_id, period)
SELECT r.report_id, r.period
FROM report r
JOIN tmp_dodam_699_context ctx ON ctx.user_id = r.user_id
JOIN tmp_dodam_699_preflight pf ON pf.ready = 1
JOIN monthly_report mr ON mr.report_id = r.report_id AND mr.report_type = r.type
WHERE r.type = 'MONTHLY'
  AND r.period BETWEEN '2026-03' AND '2026-08';

-- Preview the exact rows before the transaction mutates permanent tables.
SELECT target.report_id, target.period, mr.data_status
FROM tmp_dodam_699_report_targets target
JOIN monthly_report mr ON mr.report_id = target.report_id AND mr.report_type = 'MONTHLY'
ORDER BY target.period;

START TRANSACTION;

DELETE mr
FROM monthly_report mr
JOIN tmp_dodam_699_report_targets target ON target.report_id = mr.report_id
WHERE mr.report_type = 'MONTHLY';
SET @dodam_699_monthly_report_deleted = ROW_COUNT();

DELETE r
FROM report r
JOIN tmp_dodam_699_report_targets target ON target.report_id = r.report_id
WHERE r.type = 'MONTHLY';
SET @dodam_699_report_deleted = ROW_COUNT();

COMMIT;

SELECT
  (SELECT ready FROM tmp_dodam_699_preflight) AS source_contract_ready,
  @dodam_699_monthly_report_deleted AS monthly_report_deleted,
  @dodam_699_report_deleted AS report_deleted,
  CASE WHEN @dodam_699_monthly_report_deleted = @dodam_699_report_deleted THEN 1 ELSE 0 END AS delete_count_matches;

DROP TABLE IF EXISTS tmp_dodam_699_report_targets;
DROP TABLE IF EXISTS tmp_dodam_699_preflight;
DROP TABLE IF EXISTS tmp_dodam_699_context;
