-- DODAM Notification seed (시연 사용자 U0001~U0004의 알림함)
-- Jira: S15P21D105-519, S15P21D105-520
-- Dialect: MySQL 8.x
-- Schema basis: backend/src/main/resources/db/migration/V32__create_notification.sql (ERD notification)
-- Dependency: 491 User seed, 492 Transaction seed, 493 Goal seed, 523 Loan seed, DODAM_SUBSCRIPTION_SEED.sql
-- Dataset: 시연 사용자 4명 × 최대 6건 (결제 예정·상환 예정·잔액 부족 위험·목표 진행·월간 리포트·환영)
-- Snapshot: 2026-09-14
--
-- What this file does
-- -------------------
-- 1. 이전 실행의 seed 알림(dedupe_key LIKE 'SEED-%')을 지우고 다시 만든다. 재실행은 안전하다(멱등).
-- 2. 알림은 명세 22에 생성 API가 없어 도메인 이벤트(D10)가 만들기 전까지 시연 화면이 비어 있다. 이 파일이 U0001~U0004에
--    실제 seed 행(정기결제·대출·목표)을 가리키는 알림을 만든다. source_type/source_id 규약은 backend/docs/notification.md.
-- 3. 원천 행이 없는 사용자(예: 대출이 없는 홀수 사용자)는 해당 유형만 빠진다. 4명 외 사용자에게는 아무것도 만들지 않는다.
--
-- 시각은 seed 거래 창(2026-03-01 ~ 2026-08-31) 이후인 2026-09-01 ~ 2026-09-13에 배치했다. dedupe_key는 해시가 아닌 읽기 쉬운
-- 문자열(SEED-U0001-PAY-01, 64자 이하)이라 재실행 삭제 조건으로 쓴다. 애플리케이션이 만드는 키(SHA-256 hex)와는 형식이 달라 겹치지 않는다.
--
SET NAMES utf8mb4;
SET time_zone = '+09:00';

DELETE FROM notification WHERE dedupe_key LIKE 'SEED-%';

-- 시연 사용자 4명
DROP TABLE IF EXISTS tmp_dodam_notif_users;
CREATE TABLE tmp_dodam_notif_users (
  user_id BIGINT NOT NULL PRIMARY KEY,
  user_code CHAR(5) NOT NULL,
  name VARCHAR(100) NOT NULL
);
INSERT INTO tmp_dodam_notif_users (user_id, user_code, name)
SELECT u.user_id, CONCAT('U', SUBSTRING(u.email, 14, 4)), u.name
FROM `user` u
WHERE u.email IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                  'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid')
  AND u.deleted_at IS NULL;

-- 1. 결제 예정 (정기결제 중 다음 결제일이 가장 가까운 ACTIVE 1건) — 미확인
INSERT INTO notification (user_id, type, title, message, source_type, source_id, dedupe_key, is_read, created_at, read_at)
SELECT s.user_id, 'PAYMENT_DUE',
       CONCAT(s.name, ' 결제 예정'),
       CONCAT(DATE_FORMAT(s.next_payment_date, '%c월 %e일'), '에 ', FORMAT(s.payment_amount, 0), '원이 결제될 예정입니다. 출금 계좌 잔액을 확인해 주세요.'),
       'SUBSCRIPTION', s.subscription_id, CONCAT('SEED-', d.user_code, '-PAY-01'), 0,
       '2026-09-13 09:00:00', NULL
FROM tmp_dodam_notif_users d
JOIN subscription s ON s.user_id = d.user_id AND s.status = 'ACTIVE' AND s.next_payment_date IS NOT NULL
WHERE s.subscription_id = (
  SELECT s2.subscription_id FROM subscription s2
  WHERE s2.user_id = d.user_id AND s2.status = 'ACTIVE' AND s2.next_payment_date IS NOT NULL
  ORDER BY s2.next_payment_date, s2.subscription_id LIMIT 1);

-- 2. 상환 예정 (대출 1건, 대출이 있는 사용자만) — 미확인
INSERT INTO notification (user_id, type, title, message, source_type, source_id, dedupe_key, is_read, created_at, read_at)
SELECT l.user_id, 'REPAYMENT_DUE',
       CONCAT(l.account_name, ' 상환일 안내'),
       CONCAT(DATE_FORMAT(l.next_repayment_date, '%c월 %e일'), '이 상환일입니다. 남은 원금은 ', FORMAT(l.balance, 0), '원입니다.'),
       'LOAN', CAST(l.loan_id AS CHAR), CONCAT('SEED-', d.user_code, '-REPAY-01'), 0,
       '2026-09-12 09:00:00', NULL
FROM tmp_dodam_notif_users d
JOIN loan l ON l.user_id = d.user_id
WHERE l.loan_id = (SELECT MIN(l2.loan_id) FROM loan l2 WHERE l2.user_id = d.user_id AND l2.next_repayment_date IS NOT NULL)
  AND l.next_repayment_date IS NOT NULL;

-- 3. 잔액 부족 위험 (주거래 입출금 계좌 기준, 원천은 계좌) — 미확인
INSERT INTO notification (user_id, type, title, message, source_type, source_id, dedupe_key, is_read, created_at, read_at)
SELECT a.user_id, 'CASHFLOW_RISK',
       '이번 달 잔액 부족 위험',
       CONCAT('9월 25일 이후 예정된 결제·이체를 반영하면 ', a.account_name, ' 잔액이 부족해질 수 있어요. 현금흐름 예측에서 확인해 주세요.'),
       'ACCOUNT', CAST(a.account_id AS CHAR), CONCAT('SEED-', d.user_code, '-CASH-01'), 0,
       '2026-09-11 18:00:00', NULL
FROM tmp_dodam_notif_users d
JOIN account a ON a.user_id = d.user_id AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE'
WHERE a.account_id = (SELECT MIN(a2.account_id) FROM account a2
                      WHERE a2.user_id = d.user_id AND a2.account_type = 'DEMAND_DEPOSIT' AND a2.status = 'ACTIVE');

-- 4. 목표 진행 (모으기 목표 1건, 진행률 = 모인 금액 / 목표 금액) — 확인함
INSERT INTO notification (user_id, type, title, message, source_type, source_id, dedupe_key, is_read, created_at, read_at)
SELECT g.user_id, 'GOAL_PROGRESS',
       CONCAT('''', g.name, ''' 목표 ', LEAST(100, FLOOR(g.current_amount * 100 / GREATEST(sg.target_amount, 1))), '% 진행'),
       CONCAT('지금까지 ', FORMAT(g.current_amount, 0), '원을 모았어요. 목표 금액 ', FORMAT(sg.target_amount, 0), '원까지 ',
              FORMAT(GREATEST(sg.target_amount - g.current_amount, 0), 0), '원 남았습니다.'),
       'GOAL', CAST(g.goal_id AS CHAR), CONCAT('SEED-', d.user_code, '-GOAL-01'), 1,
       '2026-09-10 09:00:00', '2026-09-10 12:30:00'
FROM tmp_dodam_notif_users d
JOIN goal g ON g.user_id = d.user_id AND g.type = 'SAVING' AND g.deleted_at IS NULL
JOIN saving_goal sg ON sg.goal_id = g.goal_id
WHERE g.goal_id = (SELECT MIN(g2.goal_id) FROM goal g2 WHERE g2.user_id = d.user_id AND g2.type = 'SAVING' AND g2.deleted_at IS NULL);

-- 5. 월간 리포트 — 확인함
INSERT INTO notification (user_id, type, title, message, source_type, source_id, dedupe_key, is_read, created_at, read_at)
SELECT d.user_id, 'MONTHLY_REPORT',
       '8월 금융 리포트가 도착했어요',
       CONCAT(d.name, '님의 8월 소비·저축 흐름과 개선된 점을 정리했어요. 리포트에서 확인해 주세요.'),
       'REPORT', '2026-08', CONCAT('SEED-', d.user_code, '-REPORT-202608'), 1,
       '2026-09-01 09:00:00', '2026-09-01 21:10:00'
FROM tmp_dodam_notif_users d;

-- 6. 환영 (원천 없음) — 확인함
INSERT INTO notification (user_id, type, title, message, source_type, source_id, dedupe_key, is_read, created_at, read_at)
SELECT d.user_id, 'SYSTEM',
       '도담에 오신 것을 환영해요',
       '금융 데이터를 연결하면 소비 패턴 분석과 맞춤 개선안을 받을 수 있어요.',
       NULL, NULL, CONCAT('SEED-', d.user_code, '-WELCOME'), 1,
       '2026-09-07 09:00:00', '2026-09-07 09:05:00'
FROM tmp_dodam_notif_users d;

-- 검증: 사용자별 건수·미확인 수, 원천 규약 위반(둘 중 하나만 있는 행) 0
SELECT d.user_code, COUNT(n.notification_id) AS notification_count, SUM(n.is_read = 0) AS unread_count
FROM tmp_dodam_notif_users d
LEFT JOIN notification n ON n.user_id = d.user_id AND n.dedupe_key LIKE 'SEED-%'
GROUP BY d.user_code ORDER BY d.user_code;

SELECT COUNT(*) AS source_contract_violation_count
FROM notification
WHERE dedupe_key LIKE 'SEED-%' AND ((source_type IS NULL) <> (source_id IS NULL));

DROP TABLE IF EXISTS tmp_dodam_notif_users;
