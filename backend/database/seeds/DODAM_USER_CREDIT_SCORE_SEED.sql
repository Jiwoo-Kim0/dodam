-- DODAM synthetic personal credit score seed
-- Jira: S15P21D105-680 (needs V42)
-- Dialect: MySQL 8.x
-- Snapshot: 2026-09-27 23:59:59
-- Model: DODAM_CREDIT_V1
--
-- This is an explainable estimate, not a KCB/NICE score. It follows KCB's
-- published general-customer category weights: repayment history 21%, debt
-- level 24%, credit history length 9%, credit mix/usage 38%, and non-financial
-- or MyData 8%. Gender, age, name, and the hidden K-Means behavior cohort are
-- never inputs.
--
-- Required seeds: 491 users/profiles/accounts, 522 cards/installments,
-- 523 loans/minus accounts, 524 deposits/savings, and 492 transactions.
-- If demo overrides (633/636/655) are used, run this file after them. The SQL
-- updates only seed.kmeans.u0001..u4000 and is deterministic/idempotent.

SET NAMES utf8mb4;
SET time_zone = '+09:00';

DROP TABLE IF EXISTS tmp_dodam_680_users;
CREATE TABLE tmp_dodam_680_users AS
SELECT user_id, email
FROM `user`
WHERE email REGEXP '^seed[.]kmeans[.]u[0-9]{4}@dodam[.]invalid$'
  AND CAST(REGEXP_SUBSTR(email, '[0-9]{4}') AS UNSIGNED) BETWEEN 1 AND 4000;
ALTER TABLE tmp_dodam_680_users
  ADD PRIMARY KEY (user_id),
  ADD UNIQUE KEY uk_tmp_dodam_680_email (email);

-- One row per running loan. Repayment amount is taken from the loan's own
-- history because the loan table intentionally stores no monthly payment.
DROP TABLE IF EXISTS tmp_dodam_680_loan_per_account;
CREATE TABLE tmp_dodam_680_loan_per_account AS
SELECT
  loan_row.loan_id,
  loan_row.user_id,
  loan_row.balance,
  loan_row.loan_type,
  loan_row.interest_rate,
  loan_row.start_date,
  loan_row.status,
  COALESCE(MAX(repayment.amount), 0) AS monthly_payment,
  SUM(repayment.status = 'OVERDUE' AND repayment.repayment_date <= '2026-09-27') AS overdue_payment_count
FROM loan loan_row
JOIN tmp_dodam_680_users seed ON seed.user_id = loan_row.user_id
LEFT JOIN loan_repayment repayment ON repayment.loan_id = loan_row.loan_id
WHERE loan_row.status IN ('ACTIVE', 'OVERDUE')
GROUP BY loan_row.loan_id, loan_row.user_id, loan_row.balance, loan_row.loan_type,
         loan_row.interest_rate, loan_row.start_date, loan_row.status;
ALTER TABLE tmp_dodam_680_loan_per_account ADD PRIMARY KEY (loan_id), ADD KEY idx_tmp_680_loan_user (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_loan_stats;
CREATE TABLE tmp_dodam_680_loan_stats AS
SELECT
  user_id,
  COUNT(*) AS active_loan_count,
  SUM(balance) AS loan_balance,
  SUM(monthly_payment) AS monthly_loan_payment,
  SUM(status = 'OVERDUE' OR overdue_payment_count > 0) AS overdue_loan_count,
  SUM(loan_type IN ('CREDIT', 'OTHER')) AS unsecured_loan_count,
  SUM(interest_rate >= 8.0000) AS high_interest_loan_count,
  MIN(start_date) AS earliest_loan_date
FROM tmp_dodam_680_loan_per_account
GROUP BY user_id;
ALTER TABLE tmp_dodam_680_loan_stats ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_minus_stats;
CREATE TABLE tmp_dodam_680_minus_stats AS
SELECT
  minus_row.user_id,
  SUM(minus_row.limit_amount) AS minus_limit,
  SUM(minus_row.used_amount) AS minus_used,
  MAX(minus_row.overdue_days) AS minus_overdue_days,
  MIN(DATE(minus_row.created_at)) AS earliest_minus_date
FROM minus_account minus_row
JOIN tmp_dodam_680_users seed ON seed.user_id = minus_row.user_id
GROUP BY minus_row.user_id;
ALTER TABLE tmp_dodam_680_minus_stats ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_card_stats;
CREATE TABLE tmp_dodam_680_card_stats AS
SELECT
  card_row.user_id,
  SUM(card_row.status = 'ACTIVE') AS active_card_count,
  SUM(CASE WHEN card_row.status = 'ACTIVE' THEN card_row.credit_limit ELSE 0 END) AS active_credit_limit
FROM card card_row
JOIN tmp_dodam_680_users seed ON seed.user_id = card_row.user_id
GROUP BY card_row.user_id;
ALTER TABLE tmp_dodam_680_card_stats ADD PRIMARY KEY (user_id);

-- Six complete common months are used. The four demo users have September
-- transactions, but the other 3,996 do not, so September must not lower their
-- monthly average artificially.
DROP TABLE IF EXISTS tmp_dodam_680_transaction_stats;
CREATE TABLE tmp_dodam_680_transaction_stats AS
SELECT
  tx.user_id,
  SUM(tx.transaction_type = 'EXPENSE' AND tx.card_id IS NOT NULL) AS card_transaction_count,
  SUM(CASE WHEN tx.transaction_type = 'EXPENSE' AND tx.card_id IS NOT NULL THEN tx.amount ELSE 0 END) AS card_spending_6m,
  COUNT(DISTINCT CASE WHEN tx.transaction_type = 'INCOME' THEN DATE_FORMAT(tx.transaction_date, '%Y-%m') END)
    AS income_month_count,
  MIN(CASE WHEN tx.card_id IS NOT NULL THEN tx.transaction_date END) AS earliest_card_transaction_date
FROM `transaction` tx
JOIN tmp_dodam_680_users seed ON seed.user_id = tx.user_id
WHERE tx.hidden = 0
  AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-08-31'
GROUP BY tx.user_id;
ALTER TABLE tmp_dodam_680_transaction_stats ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_installment_stats;
CREATE TABLE tmp_dodam_680_installment_stats AS
SELECT
  card_row.user_id,
  SUM(installment.status = 'ACTIVE') AS active_installment_count,
  SUM(installment.status = 'OVERDUE') AS overdue_installment_count,
  SUM(CASE WHEN installment.status IN ('ACTIVE', 'OVERDUE') THEN COALESCE(installment.remaining_principal, 0) ELSE 0 END)
    AS installment_balance,
  SUM(CASE WHEN installment.status IN ('ACTIVE', 'OVERDUE') THEN installment.monthly_amount ELSE 0 END)
    AS monthly_installment_payment
FROM installment
JOIN card card_row ON card_row.card_id = installment.card_id
JOIN tmp_dodam_680_users seed ON seed.user_id = card_row.user_id
GROUP BY card_row.user_id;
ALTER TABLE tmp_dodam_680_installment_stats ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_saving_stats;
CREATE TABLE tmp_dodam_680_saving_stats AS
SELECT
  saving.user_id,
  COUNT(payment.savings_payment_id) AS saving_payment_count,
  SUM(payment.status = 'PAID') AS paid_saving_count,
  SUM(payment.status IN ('FAILED', 'SKIPPED')) AS failed_saving_count
FROM deposit_saving saving
JOIN tmp_dodam_680_users seed ON seed.user_id = saving.user_id
LEFT JOIN savings_payment payment ON payment.deposit_saving_id = saving.account_id
WHERE saving.account_type = 'SAVINGS'
GROUP BY saving.user_id;
ALTER TABLE tmp_dodam_680_saving_stats ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_asset_stats;
CREATE TABLE tmp_dodam_680_asset_stats AS
SELECT
  account_row.user_id,
  SUM(CASE WHEN account_row.account_type IN ('DEMAND_DEPOSIT', 'DEPOSIT', 'SAVINGS')
           AND account_row.status <> 'CLOSED' THEN GREATEST(account_row.balance, 0) ELSE 0 END) AS financial_assets,
  SUM(CASE WHEN account_row.account_type IN ('DEPOSIT', 'SAVINGS')
           AND account_row.status <> 'CLOSED' THEN GREATEST(account_row.balance, 0) ELSE 0 END) AS saving_assets
FROM account account_row
JOIN tmp_dodam_680_users seed ON seed.user_id = account_row.user_id
GROUP BY account_row.user_id;
ALTER TABLE tmp_dodam_680_asset_stats ADD PRIMARY KEY (user_id);

DROP TABLE IF EXISTS tmp_dodam_680_features;
CREATE TABLE tmp_dodam_680_features AS
SELECT
  seed.user_id,
  seed.email,
  profile.monthly_income,
  profile.annual_income,
  profile.emergency_fund_target,
  COALESCE(loan.active_loan_count, 0) AS active_loan_count,
  COALESCE(loan.loan_balance, 0) AS loan_balance,
  COALESCE(loan.monthly_loan_payment, 0) AS monthly_loan_payment,
  COALESCE(loan.overdue_loan_count, 0) AS overdue_loan_count,
  COALESCE(loan.unsecured_loan_count, 0) AS unsecured_loan_count,
  COALESCE(loan.high_interest_loan_count, 0) AS high_interest_loan_count,
  loan.earliest_loan_date,
  COALESCE(minus_row.minus_limit, 0) AS minus_limit,
  COALESCE(minus_row.minus_used, 0) AS minus_used,
  COALESCE(minus_row.minus_overdue_days, 0) AS minus_overdue_days,
  minus_row.earliest_minus_date,
  COALESCE(card_row.active_card_count, 0) AS active_card_count,
  COALESCE(card_row.active_credit_limit, 0) AS active_credit_limit,
  COALESCE(tx.card_transaction_count, 0) AS card_transaction_count,
  COALESCE(tx.card_spending_6m, 0) AS card_spending_6m,
  COALESCE(tx.income_month_count, 0) AS income_month_count,
  tx.earliest_card_transaction_date,
  COALESCE(installment_row.active_installment_count, 0) AS active_installment_count,
  COALESCE(installment_row.overdue_installment_count, 0) AS overdue_installment_count,
  COALESCE(installment_row.installment_balance, 0) AS installment_balance,
  COALESCE(installment_row.monthly_installment_payment, 0) AS monthly_installment_payment,
  COALESCE(saving_row.saving_payment_count, 0) AS saving_payment_count,
  COALESCE(saving_row.paid_saving_count, 0) AS paid_saving_count,
  COALESCE(saving_row.failed_saving_count, 0) AS failed_saving_count,
  COALESCE(asset_row.financial_assets, 0) AS financial_assets,
  COALESCE(asset_row.saving_assets, 0) AS saving_assets,
  CASE
    WHEN loan.earliest_loan_date IS NULL THEN tx.earliest_card_transaction_date
    WHEN tx.earliest_card_transaction_date IS NULL THEN loan.earliest_loan_date
    ELSE LEAST(loan.earliest_loan_date, tx.earliest_card_transaction_date)
  END AS earliest_credit_date
FROM tmp_dodam_680_users seed
JOIN financial_profile profile ON profile.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_loan_stats loan ON loan.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_minus_stats minus_row ON minus_row.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_card_stats card_row ON card_row.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_transaction_stats tx ON tx.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_installment_stats installment_row ON installment_row.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_saving_stats saving_row ON saving_row.user_id = seed.user_id
LEFT JOIN tmp_dodam_680_asset_stats asset_row ON asset_row.user_id = seed.user_id;
ALTER TABLE tmp_dodam_680_features ADD PRIMARY KEY (user_id), ADD UNIQUE KEY uk_tmp_680_feature_email (email);

DROP TABLE IF EXISTS tmp_dodam_680_components;
CREATE TABLE tmp_dodam_680_components AS
SELECT
  feature.*,
  GREATEST(0, 100
    - LEAST(75, feature.overdue_loan_count * 45)
    - LEAST(50, feature.overdue_installment_count * 25)
    - CASE WHEN feature.minus_overdue_days > 0
        THEN LEAST(60, 20 + feature.minus_overdue_days * 2) ELSE 0 END
  ) AS repayment_score,
  GREATEST(0, 100
    - CASE
        WHEN feature.monthly_income <= 0 THEN 65
        WHEN (feature.monthly_loan_payment + feature.monthly_installment_payment) / feature.monthly_income > 0.50 THEN 65
        WHEN (feature.monthly_loan_payment + feature.monthly_installment_payment) / feature.monthly_income > 0.40 THEN 45
        WHEN (feature.monthly_loan_payment + feature.monthly_installment_payment) / feature.monthly_income > 0.30 THEN 25
        WHEN (feature.monthly_loan_payment + feature.monthly_installment_payment) / feature.monthly_income > 0.20 THEN 10
        ELSE 0 END
    - CASE
        WHEN feature.annual_income <= 0 THEN 25
        WHEN (feature.loan_balance + feature.installment_balance + feature.minus_used) / feature.annual_income > 1.00 THEN 25
        WHEN (feature.loan_balance + feature.installment_balance + feature.minus_used) / feature.annual_income > 0.50 THEN 10
        ELSE 0 END
    - CASE
        WHEN feature.minus_limit <= 0 THEN 0
        WHEN feature.minus_used / feature.minus_limit > 0.90 THEN 40
        WHEN feature.minus_used / feature.minus_limit > 0.70 THEN 20
        WHEN feature.minus_used / feature.minus_limit > 0.30 THEN 8
        ELSE 0 END
    - LEAST(20, GREATEST(feature.active_loan_count - 1, 0) * 5)
  ) AS debt_score,
  CASE
    WHEN feature.earliest_credit_date IS NULL THEN 0
    WHEN TIMESTAMPDIFF(MONTH, feature.earliest_credit_date, '2026-09-27') < 6 THEN 20
    WHEN TIMESTAMPDIFF(MONTH, feature.earliest_credit_date, '2026-09-27') < 12 THEN 40
    WHEN TIMESTAMPDIFF(MONTH, feature.earliest_credit_date, '2026-09-27') < 24 THEN 60
    WHEN TIMESTAMPDIFF(MONTH, feature.earliest_credit_date, '2026-09-27') < 60 THEN 80
    ELSE 100
  END AS history_score,
  GREATEST(0, 100
    - CASE
        WHEN feature.active_card_count = 0 OR feature.active_credit_limit <= 0 THEN 25
        WHEN (feature.card_spending_6m / 6) / feature.active_credit_limit = 0 THEN 15
        WHEN (feature.card_spending_6m / 6) / feature.active_credit_limit <= 0.30 THEN 0
        WHEN (feature.card_spending_6m / 6) / feature.active_credit_limit <= 0.50 THEN 5
        WHEN (feature.card_spending_6m / 6) / feature.active_credit_limit <= 0.70 THEN 15
        ELSE 30 END
    - LEAST(35, feature.overdue_installment_count * 35)
    - LEAST(20, feature.active_installment_count * 5)
    - LEAST(20, feature.unsecured_loan_count * 10)
    - LEAST(20, feature.high_interest_loan_count * 10)
    - CASE
        WHEN feature.minus_limit <= 0 OR feature.minus_used = 0 THEN 0
        WHEN feature.minus_used / feature.minus_limit > 0.90 THEN 25
        ELSE 10 END
  ) AS credit_type_score,
  LEAST(100,
    40
    + CASE
        WHEN feature.saving_payment_count > 0
          THEN ROUND(30 * feature.paid_saving_count / feature.saving_payment_count)
        WHEN feature.saving_assets > 0 THEN 30
        ELSE 0 END
    + CASE
        WHEN feature.emergency_fund_target <= 0 THEN 0
        WHEN feature.financial_assets >= feature.emergency_fund_target THEN 20
        WHEN feature.financial_assets >= feature.emergency_fund_target * 0.5 THEN 10
        ELSE 0 END
    + CASE WHEN feature.income_month_count >= 6 THEN 10 WHEN feature.income_month_count >= 3 THEN 5 ELSE 0 END
  ) AS mydata_score
FROM tmp_dodam_680_features feature;
ALTER TABLE tmp_dodam_680_components ADD PRIMARY KEY (user_id), ADD UNIQUE KEY uk_tmp_680_component_email (email);

DROP TABLE IF EXISTS tmp_dodam_680_scores;
CREATE TABLE tmp_dodam_680_scores AS
SELECT
  component.user_id,
  component.email,
  component.repayment_score,
  component.debt_score,
  component.history_score,
  component.credit_type_score,
  component.mydata_score,
  ROUND(component.repayment_score * 0.21
      + component.debt_score * 0.24
      + component.history_score * 0.09
      + component.credit_type_score * 0.38
      + component.mydata_score * 0.08, 4) AS credit_index,
  CASE
    WHEN component.overdue_loan_count > 0 OR component.minus_overdue_days > 0 THEN
      LEAST(649, 300 + ROUND(7 * (component.repayment_score * 0.21
          + component.debt_score * 0.24 + component.history_score * 0.09
          + component.credit_type_score * 0.38 + component.mydata_score * 0.08)))
    WHEN component.overdue_installment_count > 0 THEN
      LEAST(699, 300 + ROUND(7 * (component.repayment_score * 0.21
          + component.debt_score * 0.24 + component.history_score * 0.09
          + component.credit_type_score * 0.38 + component.mydata_score * 0.08)))
    ELSE LEAST(1000, 300 + ROUND(7 * (component.repayment_score * 0.21
        + component.debt_score * 0.24 + component.history_score * 0.09
        + component.credit_type_score * 0.38 + component.mydata_score * 0.08)))
  END AS credit_score
FROM tmp_dodam_680_components component;
ALTER TABLE tmp_dodam_680_scores ADD PRIMARY KEY (user_id), ADD UNIQUE KEY uk_tmp_680_score_email (email);

-- Preflight fails before permanent writes when a prerequisite is incomplete or
-- any calculation escapes its contract.
DROP TABLE IF EXISTS tmp_dodam_680_preflight;
CREATE TABLE tmp_dodam_680_preflight (
  ready TINYINT NOT NULL,
  user_count INT NOT NULL,
  profile_count INT NOT NULL,
  card_user_count INT NOT NULL,
  transaction_user_count INT NOT NULL,
  score_count INT NOT NULL,
  invalid_component_count INT NOT NULL,
  invalid_score_count INT NOT NULL,
  PRIMARY KEY (ready),
  CHECK (ready = 1),
  CHECK (user_count = 4000),
  CHECK (profile_count = 4000),
  CHECK (card_user_count = 4000),
  CHECK (transaction_user_count = 4000),
  CHECK (score_count = 4000),
  CHECK (invalid_component_count = 0),
  CHECK (invalid_score_count = 0)
);

INSERT INTO tmp_dodam_680_preflight
SELECT
  1,
  (SELECT COUNT(*) FROM tmp_dodam_680_users),
  (SELECT COUNT(*) FROM financial_profile profile JOIN tmp_dodam_680_users seed ON seed.user_id = profile.user_id),
  (SELECT COUNT(DISTINCT card_row.user_id) FROM card card_row JOIN tmp_dodam_680_users seed ON seed.user_id = card_row.user_id),
  (SELECT COUNT(DISTINCT tx.user_id) FROM `transaction` tx JOIN tmp_dodam_680_users seed ON seed.user_id = tx.user_id),
  (SELECT COUNT(*) FROM tmp_dodam_680_scores),
  (SELECT COUNT(*) FROM tmp_dodam_680_scores
   WHERE repayment_score NOT BETWEEN 0 AND 100 OR debt_score NOT BETWEEN 0 AND 100
      OR history_score NOT BETWEEN 0 AND 100 OR credit_type_score NOT BETWEEN 0 AND 100
      OR mydata_score NOT BETWEEN 0 AND 100),
  (SELECT COUNT(*) FROM tmp_dodam_680_scores WHERE credit_score NOT BETWEEN 1 AND 1000);

START TRANSACTION;

UPDATE `user` user_row
JOIN tmp_dodam_680_scores score ON score.user_id = user_row.user_id
SET user_row.credit_score = score.credit_score,
    user_row.credit_score_source = 'SYNTHETIC',
    user_row.credit_score_model_version = 'DODAM_CREDIT_V1',
    user_row.credit_score_updated_at = '2026-09-27 23:59:59'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_680_preflight WHERE ready = 1);

COMMIT;

-- Expected: exactly 4,000 scores, no NULL/invalid metadata.
SELECT
  COUNT(*) AS scored_user_count,
  SUM(user_row.credit_score IS NULL) AS missing_score_count,
  SUM(user_row.credit_score NOT BETWEEN 1 AND 1000) AS invalid_score_count,
  SUM(user_row.credit_score_source <> 'SYNTHETIC'
      OR user_row.credit_score_model_version <> 'DODAM_CREDIT_V1'
      OR user_row.credit_score_updated_at <> '2026-09-27 23:59:59') AS invalid_metadata_count
FROM `user` user_row
JOIN tmp_dodam_680_users seed ON seed.user_id = user_row.user_id;

-- Distribution is observed, never quota-forced.
SELECT
  CASE
    WHEN credit_score >= 900 THEN '900-1000'
    WHEN credit_score >= 800 THEN '800-899'
    WHEN credit_score >= 700 THEN '700-799'
    WHEN credit_score >= 600 THEN '600-699'
    WHEN credit_score >= 500 THEN '500-599'
    ELSE '1-499'
  END AS score_band,
  COUNT(*) AS user_count,
  ROUND(AVG(credit_score), 1) AS average_score
FROM tmp_dodam_680_scores
GROUP BY score_band
ORDER BY MIN(credit_score) DESC;

-- Risk-direction checks: overdue users must score lower on average. Empty groups
-- return NULL rather than pretending a result.
SELECT
  ROUND(AVG(CASE WHEN component.overdue_loan_count > 0 OR component.minus_overdue_days > 0
                 THEN score.credit_score END), 1) AS overdue_average,
  ROUND(AVG(CASE WHEN component.overdue_loan_count = 0 AND component.minus_overdue_days = 0
                 THEN score.credit_score END), 1) AS non_overdue_average,
  SUM(component.overdue_loan_count > 0 OR component.minus_overdue_days > 0) AS overdue_user_count
FROM tmp_dodam_680_scores score
JOIN tmp_dodam_680_components component ON component.user_id = score.user_id;

-- Gender is excluded from the model. This query exposes accidental seed
-- correlation without requiring equal outcomes.
SELECT user_row.gender, COUNT(*) AS user_count, ROUND(AVG(user_row.credit_score), 1) AS average_score
FROM `user` user_row
JOIN tmp_dodam_680_users seed ON seed.user_id = user_row.user_id
GROUP BY user_row.gender
ORDER BY user_row.gender;

-- Presentation account U0003: no hard-coded score; show inputs, five component
-- scores, and the resulting model score for the demo checklist.
SELECT
  score.email,
  score.credit_score,
  score.credit_index,
  score.repayment_score,
  score.debt_score,
  score.history_score,
  score.credit_type_score,
  score.mydata_score,
  component.monthly_income,
  component.loan_balance,
  component.monthly_loan_payment,
  component.high_interest_loan_count,
  component.overdue_loan_count,
  component.minus_overdue_days
FROM tmp_dodam_680_scores score
JOIN tmp_dodam_680_components component ON component.user_id = score.user_id
WHERE score.email = 'seed.kmeans.u0003@dodam.invalid';

DROP TABLE IF EXISTS tmp_dodam_680_preflight;
DROP TABLE IF EXISTS tmp_dodam_680_scores;
DROP TABLE IF EXISTS tmp_dodam_680_components;
DROP TABLE IF EXISTS tmp_dodam_680_features;
DROP TABLE IF EXISTS tmp_dodam_680_asset_stats;
DROP TABLE IF EXISTS tmp_dodam_680_saving_stats;
DROP TABLE IF EXISTS tmp_dodam_680_installment_stats;
DROP TABLE IF EXISTS tmp_dodam_680_transaction_stats;
DROP TABLE IF EXISTS tmp_dodam_680_card_stats;
DROP TABLE IF EXISTS tmp_dodam_680_minus_stats;
DROP TABLE IF EXISTS tmp_dodam_680_loan_stats;
DROP TABLE IF EXISTS tmp_dodam_680_loan_per_account;
DROP TABLE IF EXISTS tmp_dodam_680_users;
