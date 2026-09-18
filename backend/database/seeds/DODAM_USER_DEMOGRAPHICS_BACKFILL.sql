-- DODAM seed user demographics backfill (birth_date / gender)
-- Jira: S15P21D105-632 (needs V39 user.birth_date, user.gender)
-- Dialect: MySQL 8.x
--
-- Purpose
-- -------
-- Fill user.birth_date and user.gender for the 4,000 seed users on a database
-- that was seeded with the 491 file BEFORE it learned to write demographics
-- (before 2026-09-17). It produces exactly the values the current 491 file
-- writes, so running 491 again later is a no-op for these two columns.
--
-- Why not re-run 491
-- ------------------
-- 491 needs the application-generated crypto fixture table (dropped by its own
-- cleanup) and it resets account.last_transaction_date to NULL, which then
-- requires re-running 492 (348k rows). This file touches only the two columns,
-- needs no fixture, and finishes in well under a second.
--
-- Rule (same as 491): seq is the 4-digit number in the seed email
-- (seed.kmeans.u0001@dodam.invalid -> 1).
--   birth_date = 2026-09-07 - (22 + MOD(seq*7, 38)) years - MOD(seq*11, 365) days
--   gender     = FEMALE when MOD(seq + FLOOR((seq-1)/4), 2) = 1, else MALE
-- The gender pattern over each block of eight users is F M F M  M F M F, so
-- both genders hold all four behavior cohorts (no cluster signal) and the demo
-- accounts U0001~U0004 are mixed (F, M, F, M). Ages 22..59 at the 2026-09-07 snapshot.
--
-- Idempotent: rows already holding these values are unchanged. Real members
-- (non-seed emails) are never touched.
SET NAMES utf8mb4;
SET time_zone = '+09:00';

UPDATE `user` u
JOIN (
  SELECT user_id, CAST(REGEXP_SUBSTR(email, '[0-9]{4}') AS UNSIGNED) AS seq
  FROM `user`
  WHERE email REGEXP '^seed[.]kmeans[.]u[0-9]{4}@dodam[.]invalid$'
) s ON s.user_id = u.user_id
SET
  u.birth_date = DATE_SUB(
    DATE_SUB('2026-09-07', INTERVAL (22 + MOD(s.seq * 7, 38)) YEAR),
    INTERVAL MOD(s.seq * 11, 365) DAY
  ),
  u.gender = CASE WHEN MOD(s.seq + FLOOR((s.seq - 1) / 4), 2) = 1 THEN 'FEMALE' ELSE 'MALE' END
WHERE s.seq BETWEEN 1 AND 4000;

-- Expected: 4,000 seed users, 0 missing, 2,000 per gender, four cohorts per band x gender.
SELECT COUNT(*) AS seed_users,
       SUM(birth_date IS NULL OR gender IS NULL) AS missing_demographics
FROM `user`
WHERE email REGEXP '^seed[.]kmeans[.]u[0-9]{4}@dodam[.]invalid$';

SELECT CONCAT(FLOOR(TIMESTAMPDIFF(YEAR, birth_date, '2026-09-07') / 10) * 10, 'S') AS age_band,
       gender,
       COUNT(*) AS user_count,
       COUNT(DISTINCT MOD(CAST(REGEXP_SUBSTR(email, '[0-9]{4}') AS UNSIGNED) - 1, 4)) AS behavior_cohort_count
FROM `user`
WHERE email REGEXP '^seed[.]kmeans[.]u[0-9]{4}@dodam[.]invalid$'
GROUP BY age_band, gender
ORDER BY age_band, gender;

-- Demo accounts (U0001~U0004) for the presentation checklist.
SELECT email, birth_date, gender
FROM `user`
WHERE email IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid')
ORDER BY email;
