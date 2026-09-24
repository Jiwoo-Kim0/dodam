-- DODAM demo user U0003 ("김싸피") realistic variable-spending story seed
-- Jira: S15P21D105-636 (시연 계정 소비 변동 조정 — U0003 이름 김싸피·월별 소비 스토리)
-- Dialect: MySQL 8.x (window functions)
-- Schema basis: V1__create_user_and_auth_session.sql (`user.name`), V6__create_transaction_category_and_transaction.sql
-- Dependency: S15P21D105-491 User/Account seed, S15P21D105-522 Card seed (primary card),
--             DODAM_TRANSACTION_SEED.sql (492, INC/FIX/SUB/SAV rows 2026-03 ~ 2026-08),
--             DODAM_DEMO_SEPTEMBER_SEED.sql (625, INC/FIX/SUB/SAV rows 2026-09)
-- Dataset: 1 user rename + U0003 variable spending 2026-03-01 ~ 2026-09-27 regenerated (about 430 rows)
-- Snapshot: 2026-09-18
--
-- Why this file exists
-- --------------------
-- 492 gives every seed user a flat variable budget (U0003: 20% of 5,700,000 = 1,140,000 every month, 16 slots,
-- merchants '도담 식당' etc.). Month-to-month totals barely move, so the report / spending trend graphs of the demo
-- account look like a straight line. The demo request asks for (1) the display name 김싸피 and (2) spending that
-- looks real and shows visible change over the months.
--
-- What this file does
-- -------------------
-- 1. UPDATE `user`.name = '김싸피' for seed.kmeans.u0003 (email·login unchanged).
-- 2. Deletes U0003's VARIABLE rows from 492 (SEED-U0003-%-VAR-%) and 625 (SEED-SEP-U0003-%-VAR-%) and regenerates them
--    from a month x category plan below. INCOME / FIXED / SUBSCRIPTION / SAVING rows of 492·625 are untouched, so income,
--    saving rate, subscription list and cashflow inputs stay as they were.
-- 3. Updates account.last_transaction_date of the primary demand-deposit account.
--
-- The story (variable spending, won)                          FOOD    CAFE   GROCERY  ...   total
-- --------------------------------------------------------------------------------------------------
--   2026-03  baseline                                        320,000  90,000  240,000        974,000
--   2026-04  quiet month                                     300,000  85,000  240,000        939,000
--   2026-05  Jeju trip (TRAVEL 480,000) + shopping spike     340,000 100,000  200,000      1,653,000
--   2026-06  back to normal, dental bills (MEDICAL 120,000)  310,000  90,000  250,000      1,015,000
--   2026-07  eating out starts to grow, wedding gift 80,000  380,000 110,000  230,000      1,031,000
--   2026-08  eating out + cafe up, grocery + shopping down    520,000 145,000  150,000        994,000
--   2026-09  (~27th) cooling down                            410,000 120,000  190,000        978,000
-- 627 CATEGORY_SPENDING_INCREASE (last month vs previous 3-month average, > 20% and >= 10,000):
--   FOOD  Aug 520,000 vs avg(May..Jul) 343,333 -> +51%  detected
--   CAFE  Aug 145,000 vs avg 100,000            -> +45%  detected
--   every other category is flat or lower in August (TAXI 24,000 vs 34,667, GROCERY 150,000 vs 226,667 ...) -> only two flagged
-- Only May is an outlier in the monthly total (about +65%); the other six months stay within about +-5% of 1,000,000.
-- Saving transfer is 400,000 a month (679 rev. 2026-09-22), i.e. about 13% of the 3,012,400 salary — not the
-- 30% the 492 cohort formula would give. Measured over the K-Means window (2026-03..08): saving rate 0.130,
-- fixed share 0.585, spending/income 0.861, so U0003 no longer sits in the saving-focused cluster.
--
-- Generation rules (deterministic, no RAND())
-- -------------------------------------------
--   h        = month_no * 104729 + plan_no * 7919 + n * 15485863 + 93         (n = 0 .. tx_count-1)
--   amount   = plan total split by weights w = 70 + MOD(h, 61); cumulative rounding to 100 won keeps the exact total
--   day      = 1 + MOD(h, 28)  (September: 27, nothing after the 27th like 625)
--   time     = CAFE 08~11h, FOOD lunch 11~13h or dinner 18~20h, others 10~20h
--   merchant = MOD(h DIV 7, merchants in group); "sequential" groups (trip, dental) use n so each merchant appears once
--   payment  = CARD (primary card) for 8 of 10 rows, ACCOUNT (primary account) otherwise; some groups are ACCOUNT only
--   ids      = SEED-U0003-2026MM-VAR-nn (Mar~Aug) / SEED-SEP-U0003-202609-VAR-nn (Sep), nn = row number inside the month
--
-- Rerun / ordering contract
-- -------------------------
-- - Run AFTER 6번(492), 8번, 11번(625). Rerunning this file alone is idempotent (deletes and regenerates U0003 VAR rows).
-- - A 492 rerun deletes 'SEED-U%' rows (this file's Mar~Aug rows included) and a 625 rerun deletes 'SEED-SEP-U%' rows
--   (this file's September rows included) and both put the flat rows back. Run this file again after either.
-- - The id prefixes are chosen so that 492 / 625 verification and cleanup treat these rows as their own.
-- - Preflight: U0003 exists, one ACTIVE primary demand-deposit account, one primary card, 11 variable categories,
--   7 monthly INCOME rows (492 six months + 625 September) present. Otherwise nothing is written.
--
-- MySQL TEMPORARY-table constraint
-- --------------------------------
-- MySQL raises ER_CANT_REOPEN_TABLE (1137) when a single statement references the same TEMPORARY table more
-- than once. Scratch tables are regular tables (tmp_dodam_u3story_*) and are dropped at the end.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------------
-- 0. context
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_u3story_context;
CREATE TABLE tmp_dodam_u3story_context (
  user_id BIGINT NOT NULL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  primary_account_id BIGINT NOT NULL,
  primary_card_id BIGINT NULL
);
INSERT INTO tmp_dodam_u3story_context (user_id, email, primary_account_id, primary_card_id)
SELECT
  u.user_id,
  u.email,
  a.account_id,
  (SELECT MIN(c.card_id) FROM `card` c WHERE c.user_id = u.user_id AND c.withdrawal_account_id = a.account_id)
FROM `user` u
JOIN `account` a
  ON a.user_id = u.user_id
 AND a.account_type = 'DEMAND_DEPOSIT'
 AND a.status = 'ACTIVE'
 AND a.source_type = 'SEED'
 AND a.account_name = '주거래 입출금'
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid'
  AND u.deleted_at IS NULL;

DROP TABLE IF EXISTS tmp_dodam_u3story_numbers;
CREATE TABLE tmp_dodam_u3story_numbers (n TINYINT NOT NULL PRIMARY KEY);
INSERT INTO tmp_dodam_u3story_numbers (n)
SELECT tens.d * 10 + ones.d
FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
CROSS JOIN (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
WHERE tens.d * 10 + ones.d < 40;

-- months: month_no 1..7 = 2026-03 .. 2026-09 (September capped at the 27th)
-- month_no 8..25 = 2024-09 .. 2026-02 (S15P21D105-706, 24-month history for the time-series model).
-- The past months are numbered AFTER the existing ones on purpose: h = month_no * 104729 + ... feeds
-- every amount, date, time and merchant pick, so renumbering 2026-03 from 1 to 19 would rewrite all
-- seven existing months. Calendar order lives in month_start, not in month_no.
DROP TABLE IF EXISTS tmp_dodam_u3story_months;
CREATE TABLE tmp_dodam_u3story_months (
  month_no TINYINT NOT NULL PRIMARY KEY,
  ym CHAR(6) NOT NULL,
  month_start DATE NOT NULL,
  day_span TINYINT NOT NULL,
  id_prefix VARCHAR(20) NOT NULL
);
INSERT INTO tmp_dodam_u3story_months VALUES
  (1, '202603', '2026-03-01', 28, 'SEED-U0003-'),
  (2, '202604', '2026-04-01', 28, 'SEED-U0003-'),
  (3, '202605', '2026-05-01', 28, 'SEED-U0003-'),
  (4, '202606', '2026-06-01', 28, 'SEED-U0003-'),
  (5, '202607', '2026-07-01', 28, 'SEED-U0003-'),
  (6, '202608', '2026-08-01', 28, 'SEED-U0003-'),
  (7, '202609', '2026-09-01', 27, 'SEED-SEP-U0003-'),
  (8, '202409', '2024-09-01', 28, 'SEED-U0003-'),
  (9, '202410', '2024-10-01', 28, 'SEED-U0003-'),
  (10, '202411', '2024-11-01', 28, 'SEED-U0003-'),
  (11, '202412', '2024-12-01', 28, 'SEED-U0003-'),
  (12, '202501', '2025-01-01', 28, 'SEED-U0003-'),
  (13, '202502', '2025-02-01', 28, 'SEED-U0003-'),
  (14, '202503', '2025-03-01', 28, 'SEED-U0003-'),
  (15, '202504', '2025-04-01', 28, 'SEED-U0003-'),
  (16, '202505', '2025-05-01', 28, 'SEED-U0003-'),
  (17, '202506', '2025-06-01', 28, 'SEED-U0003-'),
  (18, '202507', '2025-07-01', 28, 'SEED-U0003-'),
  (19, '202508', '2025-08-01', 28, 'SEED-U0003-'),
  (20, '202509', '2025-09-01', 28, 'SEED-U0003-'),
  (21, '202510', '2025-10-01', 28, 'SEED-U0003-'),
  (22, '202511', '2025-11-01', 28, 'SEED-U0003-'),
  (23, '202512', '2025-12-01', 28, 'SEED-U0003-'),
  (24, '202601', '2026-01-01', 28, 'SEED-U0003-'),
  (25, '202602', '2026-02-01', 28, 'SEED-U0003-');

-- merchant groups
DROP TABLE IF EXISTS tmp_dodam_u3story_merchants;
CREATE TABLE tmp_dodam_u3story_merchants (
  merchant_group VARCHAR(30) NOT NULL,
  pick_index TINYINT NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  PRIMARY KEY (merchant_group, pick_index)
);
INSERT INTO tmp_dodam_u3story_merchants VALUES
  ('FOOD', 0, '배달의민족'), ('FOOD', 1, '김밥천국'), ('FOOD', 2, '맘스터치'), ('FOOD', 3, '서브웨이'),
  ('FOOD', 4, '한솥도시락'), ('FOOD', 5, '명륜진사갈비'), ('FOOD', 6, '홍콩반점0410'), ('FOOD', 7, '쿠팡이츠'),
  ('FOOD', 8, '교촌치킨'), ('FOOD', 9, '본죽'),
  ('CAFE', 0, '스타벅스'), ('CAFE', 1, '투썸플레이스'), ('CAFE', 2, '메가MGC커피'), ('CAFE', 3, '이디야커피'),
  ('CAFE', 4, '컴포즈커피'), ('CAFE', 5, '폴바셋'),
  ('GROCERY', 0, '이마트'), ('GROCERY', 1, 'GS25'), ('GROCERY', 2, 'CU'), ('GROCERY', 3, '홈플러스'),
  ('GROCERY', 4, '쿠팡 로켓배송'), ('GROCERY', 5, '세븐일레븐'),
  ('PUBLIC_TRANSPORT', 0, '티머니 교통카드 충전'), ('PUBLIC_TRANSPORT', 1, '코레일'), ('PUBLIC_TRANSPORT', 2, '서울교통공사'),
  ('TAXI', 0, '카카오 T'), ('TAXI', 1, '우티'), ('TAXI', 2, '타다'),
  ('SHOPPING', 0, '쿠팡'), ('SHOPPING', 1, '무신사'), ('SHOPPING', 2, '다이소'), ('SHOPPING', 3, '네이버쇼핑'), ('SHOPPING', 4, '지그재그'),
  ('SHOPPING_BIG', 0, '나이키'), ('SHOPPING_BIG', 1, '무신사'), ('SHOPPING_BIG', 2, '애플스토어'), ('SHOPPING_BIG', 3, '쿠팡'),
  ('BEAUTY', 0, '올리브영'), ('BEAUTY', 1, '준오헤어'),
  ('CULTURE', 0, 'CGV'), ('CULTURE', 1, '메가박스'), ('CULTURE', 2, '교보문고'), ('CULTURE', 3, '인터파크 티켓'),
  ('TRAVEL_JEJU', 0, '제주항공'), ('TRAVEL_JEJU', 1, '야놀자 제주 숙소'), ('TRAVEL_JEJU', 2, '롯데렌터카 제주'), ('TRAVEL_JEJU', 3, '성산일출봉 매표소'),
  ('MEDICAL', 0, '연세바른내과'), ('MEDICAL', 1, '온누리약국'),
  ('MEDICAL_DENTAL', 0, '서울미소치과'),
  ('ETC', 0, '다이소'), ('ETC', 1, '무인양품'), ('ETC', 2, '우체국'),
  ('ETC_GIFT', 0, '결혼식 축의금');

-- month x category plan. total_amount is a multiple of 100 so the weighted split sums exactly.
DROP TABLE IF EXISTS tmp_dodam_u3story_plan;
CREATE TABLE tmp_dodam_u3story_plan (
  plan_no INT NOT NULL PRIMARY KEY,
  month_no TINYINT NOT NULL,
  category_code VARCHAR(50) NOT NULL,
  merchant_group VARCHAR(30) NOT NULL,
  total_amount BIGINT NOT NULL,
  tx_count TINYINT NOT NULL,
  sequential_merchants TINYINT(1) NOT NULL DEFAULT 0,
  account_only TINYINT(1) NOT NULL DEFAULT 0,
  CHECK (total_amount > 0 AND MOD(total_amount, 100) = 0),
  CHECK (tx_count BETWEEN 1 AND 40)
);
INSERT INTO tmp_dodam_u3story_plan (plan_no, month_no, category_code, merchant_group, total_amount, tx_count, sequential_merchants, account_only) VALUES
  -- 2026-03 (974,000)
  (101, 1, 'FOOD', 'FOOD', 320000, 22, 0, 0), (102, 1, 'CAFE', 'CAFE', 90000, 18, 0, 0), (103, 1, 'GROCERY', 'GROCERY', 240000, 8, 0, 0),
  (104, 1, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (105, 1, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (106, 1, 'SHOPPING', 'SHOPPING', 140000, 3, 0, 0), (107, 1, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (108, 1, 'CULTURE', 'CULTURE', 30000, 1, 0, 0),
  (109, 1, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2026-04 (939,000)
  (201, 2, 'FOOD', 'FOOD', 300000, 21, 0, 0), (202, 2, 'CAFE', 'CAFE', 85000, 17, 0, 0), (203, 2, 'GROCERY', 'GROCERY', 240000, 8, 0, 0),
  (204, 2, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (205, 2, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (206, 2, 'SHOPPING', 'SHOPPING', 130000, 3, 0, 0), (207, 2, 'CULTURE', 'CULTURE', 45000, 2, 0, 0), (208, 2, 'MEDICAL', 'MEDICAL', 35000, 1, 0, 0),
  (209, 2, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2026-05 (1,653,000) — Jeju trip + shopping spike
  (301, 3, 'FOOD', 'FOOD', 340000, 23, 0, 0), (302, 3, 'CAFE', 'CAFE', 100000, 19, 0, 0), (303, 3, 'GROCERY', 'GROCERY', 200000, 7, 0, 0),
  (304, 3, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (305, 3, 'TAXI', 'TAXI', 48000, 3, 0, 0),
  (306, 3, 'SHOPPING', 'SHOPPING_BIG', 310000, 4, 1, 0), (307, 3, 'BEAUTY', 'BEAUTY', 60000, 1, 0, 0), (308, 3, 'CULTURE', 'CULTURE', 30000, 1, 0, 0),
  (309, 3, 'TRAVEL', 'TRAVEL_JEJU', 480000, 4, 1, 0), (310, 3, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2026-06 (1,015,000) — dental
  (401, 4, 'FOOD', 'FOOD', 310000, 22, 0, 0), (402, 4, 'CAFE', 'CAFE', 90000, 18, 0, 0), (403, 4, 'GROCERY', 'GROCERY', 250000, 9, 0, 0),
  (404, 4, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (405, 4, 'TAXI', 'TAXI', 20000, 1, 0, 0),
  (406, 4, 'SHOPPING', 'SHOPPING', 80000, 2, 0, 0), (407, 4, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (408, 4, 'CULTURE', 'CULTURE', 15000, 1, 0, 0),
  (409, 4, 'MEDICAL', 'MEDICAL_DENTAL', 120000, 2, 0, 0), (410, 4, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2026-07 (1,031,000) — eating out starts to grow, wedding gift
  (501, 5, 'FOOD', 'FOOD', 380000, 25, 0, 0), (502, 5, 'CAFE', 'CAFE', 110000, 21, 0, 0), (503, 5, 'GROCERY', 'GROCERY', 230000, 8, 0, 0),
  (504, 5, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (505, 5, 'TAXI', 'TAXI', 36000, 2, 0, 0),
  (506, 5, 'SHOPPING', 'SHOPPING', 80000, 2, 0, 0), (507, 5, 'CULTURE', 'CULTURE', 30000, 1, 0, 0),
  (508, 5, 'ETC', 'ETC', 20000, 1, 0, 0), (509, 5, 'ETC', 'ETC_GIFT', 80000, 1, 1, 1),
  -- 2026-08 (994,000) — food + cafe up, grocery/shopping down: total stays flat, 627 detects only FOOD, CAFE
  (601, 6, 'FOOD', 'FOOD', 520000, 30, 0, 0), (602, 6, 'CAFE', 'CAFE', 145000, 26, 0, 0), (603, 6, 'GROCERY', 'GROCERY', 150000, 6, 0, 0),
  (604, 6, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (605, 6, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (606, 6, 'SHOPPING', 'SHOPPING', 60000, 2, 0, 0), (608, 6, 'CULTURE', 'CULTURE', 15000, 1, 0, 0),
  (609, 6, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2026-09 ~27th (978,000) — cooling down
  (701, 7, 'FOOD', 'FOOD', 410000, 24, 0, 0), (702, 7, 'CAFE', 'CAFE', 120000, 20, 0, 0), (703, 7, 'GROCERY', 'GROCERY', 190000, 7, 0, 0),
  (704, 7, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (705, 7, 'TAXI', 'TAXI', 48000, 3, 0, 0),
  (706, 7, 'SHOPPING', 'SHOPPING', 100000, 2, 0, 0), (707, 7, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (708, 7, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 706: past 18 months (month_no 8..25). FOOD/CAFE ramp up toward 2026-08 so the model reads a
  -- rising trend; December gets a SHOPPING_BIG spike so the seasonal index has two observations.
  -- BEAUTY and ETC are placed irregularly on purpose. An every-other-month rule would line up with
  -- calendar-month parity and the estimator would read it as seasonality, which is the same defect
  -- the VOLATILE cohort already has in 492.
  -- 2024-09 (800,000)
  (801, 8, 'FOOD', 'FOOD', 258000, 17, 0, 0), (802, 8, 'CAFE', 'CAFE', 71000, 13, 0, 0), (803, 8, 'GROCERY', 'GROCERY', 203000, 7, 0, 0),
  (804, 8, 'SHOPPING', 'SHOPPING', 82000, 2, 0, 0), (805, 8, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (806, 8, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (807, 8, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (808, 8, 'BEAUTY', 'BEAUTY', 52000, 1, 0, 0), (809, 8, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2024-10 (830,000)
  (901, 9, 'FOOD', 'FOOD', 293000, 20, 0, 0), (902, 9, 'CAFE', 'CAFE', 80000, 15, 0, 0), (903, 9, 'GROCERY', 'GROCERY', 226000, 8, 0, 0),
  (904, 9, 'SHOPPING', 'SHOPPING', 92000, 2, 0, 0), (905, 9, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (906, 9, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (907, 9, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (908, 9, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2024-11 (850,000)
  (1001, 10, 'FOOD', 'FOOD', 289000, 19, 0, 0), (1002, 10, 'CAFE', 'CAFE', 80000, 15, 0, 0), (1003, 10, 'GROCERY', 'GROCERY', 218000, 7, 0, 0),
  (1004, 10, 'SHOPPING', 'SHOPPING', 89000, 2, 0, 0), (1005, 10, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1006, 10, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1007, 10, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1008, 10, 'MEDICAL', 'MEDICAL', 35000, 1, 0, 0), (1009, 10, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2024-12 (1,020,000)
  (1101, 11, 'FOOD', 'FOOD', 274000, 18, 0, 0), (1102, 11, 'CAFE', 'CAFE', 85000, 15, 0, 0), (1103, 11, 'GROCERY', 'GROCERY', 203000, 7, 0, 0),
  (1104, 11, 'SHOPPING', 'SHOPPING_BIG', 233000, 4, 1, 0), (1105, 11, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1106, 11, 'TAXI', 'TAXI', 48000, 3, 0, 0),
  (1107, 11, 'CULTURE', 'CULTURE', 45000, 1, 0, 0), (1108, 11, 'BEAUTY', 'BEAUTY', 52000, 1, 0, 0), (1109, 11, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-01 (810,000)
  (1201, 12, 'FOOD', 'FOOD', 274000, 18, 0, 0), (1202, 12, 'CAFE', 'CAFE', 76000, 14, 0, 0), (1203, 12, 'GROCERY', 'GROCERY', 199000, 7, 0, 0),
  (1204, 12, 'SHOPPING', 'SHOPPING', 82000, 2, 0, 0), (1205, 12, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1206, 12, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1207, 12, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1208, 12, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (1209, 12, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-02 (740,000)
  (1301, 13, 'FOOD', 'FOOD', 263000, 18, 0, 0), (1302, 13, 'CAFE', 'CAFE', 74000, 13, 0, 0), (1303, 13, 'GROCERY', 'GROCERY', 188000, 6, 0, 0),
  (1304, 13, 'SHOPPING', 'SHOPPING', 76000, 2, 0, 0), (1305, 13, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1306, 13, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1307, 13, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1308, 13, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2025-03 (825,000)
  (1401, 14, 'FOOD', 'FOOD', 305000, 20, 0, 0), (1402, 14, 'CAFE', 'CAFE', 86000, 16, 0, 0), (1403, 14, 'GROCERY', 'GROCERY', 213000, 7, 0, 0),
  (1404, 14, 'SHOPPING', 'SHOPPING', 87000, 2, 0, 0), (1405, 14, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1406, 14, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1407, 14, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1408, 14, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-04 (800,000)
  (1501, 15, 'FOOD', 'FOOD', 276000, 18, 0, 0), (1502, 15, 'CAFE', 'CAFE', 78000, 14, 0, 0), (1503, 15, 'GROCERY', 'GROCERY', 190000, 6, 0, 0),
  (1504, 15, 'SHOPPING', 'SHOPPING', 77000, 2, 0, 0), (1505, 15, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1506, 15, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1507, 15, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1508, 15, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (1509, 15, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-05 (1,085,000)
  (1601, 16, 'FOOD', 'FOOD', 279000, 19, 0, 0), (1602, 16, 'CAFE', 'CAFE', 79000, 14, 0, 0), (1603, 16, 'GROCERY', 'GROCERY', 188000, 6, 0, 0),
  (1604, 16, 'SHOPPING', 'SHOPPING', 76000, 2, 0, 0), (1605, 16, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1606, 16, 'TAXI', 'TAXI', 48000, 3, 0, 0),
  (1607, 16, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1608, 16, 'TRAVEL', 'TRAVEL_JEJU', 300000, 4, 1, 0), (1609, 16, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2025-06 (870,000)
  (1701, 17, 'FOOD', 'FOOD', 293000, 20, 0, 0), (1702, 17, 'CAFE', 'CAFE', 83000, 15, 0, 0), (1703, 17, 'GROCERY', 'GROCERY', 194000, 6, 0, 0),
  (1704, 17, 'SHOPPING', 'SHOPPING', 79000, 2, 0, 0), (1705, 17, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1706, 17, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1707, 17, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (1708, 17, 'BEAUTY', 'BEAUTY', 52000, 1, 0, 0), (1709, 17, 'MEDICAL', 'MEDICAL', 35000, 1, 0, 0),
  (1710, 17, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-07 (905,000)
  (1801, 18, 'FOOD', 'FOOD', 324000, 22, 0, 0), (1802, 18, 'CAFE', 'CAFE', 92000, 17, 0, 0), (1803, 18, 'GROCERY', 'GROCERY', 210000, 7, 0, 0),
  (1804, 18, 'SHOPPING', 'SHOPPING', 85000, 2, 0, 0), (1805, 18, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1806, 18, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1807, 18, 'CULTURE', 'CULTURE', 45000, 1, 0, 0), (1808, 18, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (1809, 18, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-08 (880,000)
  (1901, 19, 'FOOD', 'FOOD', 333000, 22, 0, 0), (1902, 19, 'CAFE', 'CAFE', 95000, 17, 0, 0), (1903, 19, 'GROCERY', 'GROCERY', 211000, 7, 0, 0),
  (1904, 19, 'SHOPPING', 'SHOPPING', 87000, 2, 0, 0), (1905, 19, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (1906, 19, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (1907, 19, 'CULTURE', 'CULTURE', 45000, 1, 0, 0), (1908, 19, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2025-09 (940,000)
  (2001, 20, 'FOOD', 'FOOD', 370000, 25, 0, 0), (2002, 20, 'CAFE', 'CAFE', 106000, 19, 0, 0), (2003, 20, 'GROCERY', 'GROCERY', 230000, 8, 0, 0),
  (2004, 20, 'SHOPPING', 'SHOPPING', 95000, 2, 0, 0), (2005, 20, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (2006, 20, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (2007, 20, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (2008, 20, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2025-10 (980,000)
  (2101, 21, 'FOOD', 'FOOD', 373000, 25, 0, 0), (2102, 21, 'CAFE', 'CAFE', 107000, 19, 0, 0), (2103, 21, 'GROCERY', 'GROCERY', 228000, 8, 0, 0),
  (2104, 21, 'SHOPPING', 'SHOPPING', 93000, 2, 0, 0), (2105, 21, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (2106, 21, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (2107, 21, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (2108, 21, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (2109, 21, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-11 (1,010,000)
  (2201, 22, 'FOOD', 'FOOD', 395000, 26, 0, 0), (2202, 22, 'CAFE', 'CAFE', 114000, 21, 0, 0), (2203, 22, 'GROCERY', 'GROCERY', 236000, 8, 0, 0),
  (2204, 22, 'SHOPPING', 'SHOPPING', 96000, 2, 0, 0), (2205, 22, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (2206, 22, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (2207, 22, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (2208, 22, 'MEDICAL', 'MEDICAL', 35000, 1, 0, 0), (2209, 22, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2025-12 (1,210,000)
  (2301, 23, 'FOOD', 'FOOD', 373000, 25, 0, 0), (2302, 23, 'CAFE', 'CAFE', 122000, 22, 0, 0), (2303, 23, 'GROCERY', 'GROCERY', 218000, 7, 0, 0),
  (2304, 23, 'SHOPPING', 'SHOPPING_BIG', 272000, 4, 1, 0), (2305, 23, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (2306, 23, 'TAXI', 'TAXI', 48000, 3, 0, 0),
  (2307, 23, 'CULTURE', 'CULTURE', 45000, 1, 0, 0), (2308, 23, 'BEAUTY', 'BEAUTY', 52000, 1, 0, 0), (2309, 23, 'ETC', 'ETC', 15000, 1, 0, 0),
  -- 2026-01 (960,000)
  (2401, 24, 'FOOD', 'FOOD', 370000, 25, 0, 0), (2402, 24, 'CAFE', 'CAFE', 107000, 19, 0, 0), (2403, 24, 'GROCERY', 'GROCERY', 212000, 7, 0, 0),
  (2404, 24, 'SHOPPING', 'SHOPPING', 87000, 2, 0, 0), (2405, 24, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (2406, 24, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (2407, 24, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (2408, 24, 'BEAUTY', 'BEAUTY', 45000, 1, 0, 0), (2409, 24, 'ETC', 'ETC', 20000, 1, 0, 0),
  -- 2026-02 (870,000)
  (2501, 25, 'FOOD', 'FOOD', 351000, 23, 0, 0), (2502, 25, 'CAFE', 'CAFE', 102000, 19, 0, 0), (2503, 25, 'GROCERY', 'GROCERY', 197000, 7, 0, 0),
  (2504, 25, 'SHOPPING', 'SHOPPING', 81000, 2, 0, 0), (2505, 25, 'PUBLIC_TRANSPORT', 'PUBLIC_TRANSPORT', 65000, 3, 0, 0), (2506, 25, 'TAXI', 'TAXI', 24000, 2, 0, 0),
  (2507, 25, 'CULTURE', 'CULTURE', 30000, 1, 0, 0), (2508, 25, 'ETC', 'ETC', 20000, 1, 0, 0);

DROP TABLE IF EXISTS tmp_dodam_u3story_categories;
CREATE TABLE tmp_dodam_u3story_categories (code VARCHAR(50) NOT NULL PRIMARY KEY, category_id BIGINT NOT NULL);
INSERT INTO tmp_dodam_u3story_categories (code, category_id)
SELECT code, category_id FROM transaction_category
WHERE code IN ('FOOD', 'CAFE', 'GROCERY', 'PUBLIC_TRANSPORT', 'TAXI', 'SHOPPING', 'BEAUTY', 'CULTURE', 'TRAVEL', 'MEDICAL', 'ETC')
  AND active = 1;

-- ---------------------------------------------------------------------------
-- 1. preflight (one row when every check passes)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_u3story_preflight;
CREATE TABLE tmp_dodam_u3story_preflight (
  ready TINYINT NOT NULL PRIMARY KEY,
  context_count INT NOT NULL,
  card_count INT NOT NULL,
  category_count INT NOT NULL,
  income_month_count INT NOT NULL,
  past_income_month_count INT NOT NULL,
  plan_group_missing INT NOT NULL
);
INSERT INTO tmp_dodam_u3story_preflight
SELECT 1, m.context_count, m.card_count, m.category_count, m.income_month_count, m.past_income_month_count, m.plan_group_missing
FROM (
  SELECT
    (SELECT COUNT(*) FROM tmp_dodam_u3story_context) AS context_count,
    (SELECT COUNT(*) FROM tmp_dodam_u3story_context WHERE primary_card_id IS NOT NULL) AS card_count,
    (SELECT COUNT(*) FROM tmp_dodam_u3story_categories) AS category_count,
    (SELECT COUNT(DISTINCT DATE_FORMAT(tx.transaction_date, '%Y%m'))
       FROM `transaction` tx
       JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED' AND tx.transaction_type = 'INCOME'
        AND tx.transaction_date BETWEEN '2026-03-01' AND '2026-09-30') AS income_month_count,
    -- 706: the past block must already exist (492 extended) or this file would build a story on top of nothing.
    (SELECT COUNT(DISTINCT DATE_FORMAT(tx.transaction_date, '%Y%m'))
       FROM `transaction` tx
       JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
      WHERE tx.source_type = 'SEED' AND tx.transaction_type = 'INCOME'
        AND tx.transaction_date BETWEEN '2024-09-01' AND '2026-02-28') AS past_income_month_count,
    (SELECT COUNT(*) FROM tmp_dodam_u3story_plan p
      WHERE NOT EXISTS (SELECT 1 FROM tmp_dodam_u3story_merchants mm WHERE mm.merchant_group = p.merchant_group)
         OR NOT EXISTS (SELECT 1 FROM tmp_dodam_u3story_categories cc WHERE cc.code = p.category_code)) AS plan_group_missing
) m
WHERE m.context_count = 1 AND m.card_count = 1 AND m.category_count = 11 AND m.income_month_count = 7
  AND m.past_income_month_count = 18 AND m.plan_group_missing = 0;

SELECT * FROM tmp_dodam_u3story_preflight;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 2. display name
-- ---------------------------------------------------------------------------
UPDATE `user` u
JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = u.user_id
SET u.name = '김싸피', u.updated_at = NOW()
WHERE EXISTS (SELECT 1 FROM tmp_dodam_u3story_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 3. remove U0003's previous variable rows (492 flat rows, 625 September rows, and this file's previous run)
-- ---------------------------------------------------------------------------
DELETE tx FROM `transaction` tx
JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
  AND (tx.transaction_id LIKE 'SEED-U0003-%-VAR-%' OR tx.transaction_id LIKE 'SEED-SEP-U0003-%-VAR-%')
  AND EXISTS (SELECT 1 FROM tmp_dodam_u3story_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 4. generate
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_u3story_rows;
CREATE TABLE tmp_dodam_u3story_rows (
  plan_no INT NOT NULL,
  n TINYINT NOT NULL,
  h BIGINT NOT NULL,
  w INT NOT NULL,
  amount BIGINT NOT NULL,
  PRIMARY KEY (plan_no, n)
);
INSERT INTO tmp_dodam_u3story_rows (plan_no, n, h, w, amount)
SELECT
  x.plan_no, x.n, x.h, x.w,
  ROUND(x.total_amount * x.cum_w / x.sum_w, -2) - ROUND(x.total_amount * (x.cum_w - x.w) / x.sum_w, -2)
FROM (
  SELECT
    g.plan_no, g.n, g.h, g.w, g.total_amount,
    SUM(g.w) OVER (PARTITION BY g.plan_no ORDER BY g.n) AS cum_w,
    SUM(g.w) OVER (PARTITION BY g.plan_no) AS sum_w
  FROM (
    SELECT
      p.plan_no, num.n, p.total_amount,
      (p.month_no * 104729 + p.plan_no * 7919 + num.n * 15485863 + 93) AS h,
      70 + MOD(p.month_no * 104729 + p.plan_no * 7919 + num.n * 15485863 + 93, 61) AS w
    FROM tmp_dodam_u3story_plan p
    JOIN tmp_dodam_u3story_numbers num ON num.n < p.tx_count
  ) g
) x;

INSERT INTO `transaction` (
  transaction_id, user_id, account_id, card_id, category_id,
  transaction_date, transaction_time, transaction_type, amount, balance_after,
  merchant_name, payment_method, spending_type, summary, memo,
  hidden, source_type, created_at, updated_at
)
SELECT
  CONCAT(m.id_prefix, m.ym, '-VAR-', LPAD(ROW_NUMBER() OVER (PARTITION BY m.month_no ORDER BY r.plan_no, r.n), 2, '0')),
  ctx.user_id,
  CASE WHEN p.account_only = 1 OR MOD(r.h, 10) >= 8 THEN ctx.primary_account_id ELSE NULL END,
  CASE WHEN p.account_only = 1 OR MOD(r.h, 10) >= 8 THEN NULL ELSE ctx.primary_card_id END,
  cat.category_id,
  DATE_ADD(m.month_start, INTERVAL MOD(r.h, m.day_span) DAY),
  CASE p.merchant_group
    WHEN 'CAFE' THEN MAKETIME(8 + MOD(r.h DIV 13, 4), MOD(r.h DIV 97, 60), 0)
    WHEN 'FOOD' THEN MAKETIME(CASE WHEN MOD(r.h DIV 11, 2) = 0 THEN 11 + MOD(r.h DIV 13, 3) ELSE 18 + MOD(r.h DIV 13, 3) END, MOD(r.h DIV 97, 60), 0)
    ELSE MAKETIME(10 + MOD(r.h DIV 13, 11), MOD(r.h DIV 97, 60), 0)
  END,
  'EXPENSE',
  r.amount,
  NULL,
  mer.merchant_name,
  CASE WHEN p.account_only = 1 OR MOD(r.h, 10) >= 8 THEN 'ACCOUNT' ELSE 'CARD' END,
  'VARIABLE',
  mer.merchant_name,
  NULL,
  0, 'SEED', '2026-09-18 09:00:00', NULL
FROM tmp_dodam_u3story_rows r
JOIN tmp_dodam_u3story_plan p ON p.plan_no = r.plan_no
JOIN tmp_dodam_u3story_months m ON m.month_no = p.month_no
JOIN tmp_dodam_u3story_categories cat ON cat.code = p.category_code
JOIN (SELECT merchant_group, COUNT(*) AS cnt FROM tmp_dodam_u3story_merchants GROUP BY merchant_group) mc
  ON mc.merchant_group = p.merchant_group
JOIN tmp_dodam_u3story_merchants mer
  ON mer.merchant_group = p.merchant_group
 AND mer.pick_index = CASE WHEN p.sequential_merchants = 1 THEN MOD(r.n, mc.cnt) ELSE MOD(r.h DIV 7, mc.cnt) END
CROSS JOIN tmp_dodam_u3story_context ctx
WHERE EXISTS (SELECT 1 FROM tmp_dodam_u3story_preflight pf WHERE pf.ready = 1);

-- ---------------------------------------------------------------------------
-- 5. account.last_transaction_date (ACCOUNT rows touch the account)
-- ---------------------------------------------------------------------------
UPDATE `account` a
JOIN tmp_dodam_u3story_context ctx ON ctx.primary_account_id = a.account_id
JOIN (
  SELECT tx.account_id, MAX(tx.transaction_date) AS last_date
  FROM `transaction` tx
  JOIN tmp_dodam_u3story_context c ON c.primary_account_id = tx.account_id
  WHERE tx.source_type = 'SEED'
  GROUP BY tx.account_id
) latest ON latest.account_id = a.account_id
SET a.last_transaction_date = latest.last_date,
    a.updated_at = '2026-09-18 09:00:00'
WHERE EXISTS (SELECT 1 FROM tmp_dodam_u3story_preflight pf WHERE pf.ready = 1);

COMMIT;

-- ---------------------------------------------------------------------------
-- 6. verification
-- ---------------------------------------------------------------------------
-- Expected: name 김싸피
SELECT u.email, u.name FROM `user` u JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = u.user_id;

-- Expected: monthly variable totals 974,000 / 939,000 / 1,653,000 / 1,015,000 / 1,031,000 / 994,000 / 978,000
--           (row counts 59 / 58 / 66 / 60 / 64 / 71 / 61), plan_gap 0 for every month.
SELECT
  actual.ym,
  actual.row_count,
  actual.variable_total,
  actual.variable_total - planned.plan_total AS plan_gap,
  actual.food,
  actual.cafe,
  actual.travel,
  actual.first_date,
  actual.last_date,
  actual.min_amount
FROM (
  SELECT
    DATE_FORMAT(tx.transaction_date, '%Y%m') AS ym,
    COUNT(*) AS row_count,
    SUM(tx.amount) AS variable_total,
    SUM(CASE WHEN tc.code = 'FOOD' THEN tx.amount ELSE 0 END) AS food,
    SUM(CASE WHEN tc.code = 'CAFE' THEN tx.amount ELSE 0 END) AS cafe,
    SUM(CASE WHEN tc.code = 'TRAVEL' THEN tx.amount ELSE 0 END) AS travel,
    MIN(tx.transaction_date) AS first_date,
    MAX(tx.transaction_date) AS last_date,
    MIN(tx.amount) AS min_amount
  FROM `transaction` tx
  JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
  JOIN transaction_category tc ON tc.category_id = tx.category_id
  WHERE tx.source_type = 'SEED' AND tx.spending_type = 'VARIABLE'
  GROUP BY DATE_FORMAT(tx.transaction_date, '%Y%m')
) actual
JOIN (
  SELECT mm.ym, SUM(p.total_amount) AS plan_total
  FROM tmp_dodam_u3story_plan p
  JOIN tmp_dodam_u3story_months mm ON mm.month_no = p.month_no
  GROUP BY mm.ym
) planned ON planned.ym = actual.ym
ORDER BY actual.ym;

-- Expected: FOOD and CAFE increase > 20% (627 rule: August vs May..July average); nothing else above 20% with >= 10,000.
SELECT
  tc.code,
  SUM(CASE WHEN tx.transaction_date BETWEEN '2026-08-01' AND '2026-08-31' THEN tx.amount ELSE 0 END) AS august,
  ROUND(SUM(CASE WHEN tx.transaction_date BETWEEN '2026-05-01' AND '2026-07-31' THEN tx.amount ELSE 0 END) / 3) AS avg_may_jul,
  ROUND((SUM(CASE WHEN tx.transaction_date BETWEEN '2026-08-01' AND '2026-08-31' THEN tx.amount ELSE 0 END)
         / NULLIF(SUM(CASE WHEN tx.transaction_date BETWEEN '2026-05-01' AND '2026-07-31' THEN tx.amount ELSE 0 END) / 3, 0) - 1) * 100, 1)
    AS increase_percent
FROM `transaction` tx
JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
JOIN transaction_category tc ON tc.category_id = tx.category_id
WHERE tx.source_type = 'SEED' AND tx.spending_type = 'VARIABLE'
GROUP BY tc.code
HAVING august > 0
ORDER BY increase_percent DESC;

-- Expected: 0 (positive amounts, exactly one of account_id / card_id, dates inside the window, ids unique by definition)
SELECT COUNT(*) AS contract_violation_count
FROM `transaction` tx
JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED' AND tx.spending_type = 'VARIABLE'
  AND (tx.amount <= 0
       OR (tx.account_id IS NULL AND tx.card_id IS NULL)
       OR (tx.account_id IS NOT NULL AND tx.card_id IS NOT NULL)
       OR tx.transaction_date < '2026-03-01' OR tx.transaction_date > '2026-09-27');

-- Expected: INC 7, FIX 21, SUB 7, SAV 7 untouched (492 six months + 625 September)
SELECT
  CASE WHEN tx.transaction_id LIKE '%-INC-%' THEN 'INC' WHEN tx.transaction_id LIKE '%-FIX-%' THEN 'FIX'
       WHEN tx.transaction_id LIKE '%-SUB-%' THEN 'SUB' WHEN tx.transaction_id LIKE '%-SAV-%' THEN 'SAV' ELSE 'VAR' END AS kind,
  COUNT(*) AS row_count
FROM `transaction` tx
JOIN tmp_dodam_u3story_context ctx ON ctx.user_id = tx.user_id
WHERE tx.source_type = 'SEED'
GROUP BY kind
ORDER BY kind;

-- ---------------------------------------------------------------------------
-- 7. cleanup
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_dodam_u3story_rows;
DROP TABLE IF EXISTS tmp_dodam_u3story_preflight;
DROP TABLE IF EXISTS tmp_dodam_u3story_categories;
DROP TABLE IF EXISTS tmp_dodam_u3story_plan;
DROP TABLE IF EXISTS tmp_dodam_u3story_merchants;
DROP TABLE IF EXISTS tmp_dodam_u3story_months;
DROP TABLE IF EXISTS tmp_dodam_u3story_numbers;
DROP TABLE IF EXISTS tmp_dodam_u3story_context;
