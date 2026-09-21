-- S15P21D105-678: accountId 기준 만기 예상 API의 결정적 fixture. 전용 H2 DB에 매 테스트 재적재하며 운영 seed가 아니다.
-- 기준일 2026-09-21 기준으로 다음을 만든다.
--   계좌 1  예금 10,000,000원 / 3.0% / 2026-01-01 ~ 2027-01-01 → COMPLETE
--   계좌 2  진행 중 적금(월 1,000,000 / 15일 / 3.25% / 2020-04-15 ~ 2027-04-15, 78회 납입) → COMPLETE, 678 시연 목돈적금과 같은 계약
--   계좌 3  만기일 없는 적금(주택청약형) → UNAVAILABLE / INCOMPLETE_SAVINGS_TERMS
--   계좌 4  상세행 없는 예금 → UNAVAILABLE / MISSING_DEPOSIT_DETAIL
--   계좌 5  입출금계좌 → 404
--   계좌 6  타인의 예금 → 404
-- 계좌번호 해시는 UNIQUE 충족용 자리값이며 실제 민감번호가 아니다. 이 API는 해시를 쓰지 않는다.
ALTER TABLE account ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE account ADD COLUMN IF NOT EXISTS balance BIGINT;
DELETE FROM savings_payment;
DELETE FROM deposit_saving;
DELETE FROM account;
INSERT INTO account
    (account_id, user_id, bank_code, bank_name, account_no_masked, account_no_hash,
     account_name, account_type, status, balance, currency, created_at, updated_at)
VALUES
    (1, 1, 'TEST', '시연은행', '***-01', 'forecast-hash-1', '예금', 'DEPOSIT', 'ACTIVE', 10000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-21 00:00:00'),
    (2, 1, 'TEST', '시연은행', '***-02', 'forecast-hash-2', '내집마련 목돈적금', 'SAVINGS', 'ACTIVE', 78000000, 'KRW', TIMESTAMP '2020-04-15 00:00:00', TIMESTAMP '2026-09-21 00:00:00'),
    (3, 1, 'TEST', '시연은행', '***-03', 'forecast-hash-3', '주택청약종합저축', 'SAVINGS', 'ACTIVE', 200000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-21 00:00:00'),
    (4, 1, 'TEST', '시연은행', '***-04', 'forecast-hash-4', '상세 누락 예금', 'DEPOSIT', 'ACTIVE', 50000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-21 00:00:00'),
    (5, 1, 'TEST', '시연은행', '***-05', 'forecast-hash-5', '주거래 입출금', 'DEMAND_DEPOSIT', 'ACTIVE', 300000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-21 00:00:00'),
    (6, 2, 'TEST', '시연은행', '***-06', 'forecast-hash-6', '타인 예금', 'DEPOSIT', 'ACTIVE', 5000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-21 00:00:00');
INSERT INTO deposit_saving
    (account_id, user_id, withdrawal_account_id, withdrawal_account_type, account_type, interest_rate,
     monthly_amount, payment_day, next_payment_date, opening_date, expiry_date, created_at)
VALUES
    (1, 1, NULL, NULL, 'DEPOSIT', 3.0000, NULL, NULL, NULL, DATE '2026-01-01', DATE '2027-01-01', TIMESTAMP '2026-01-01 00:00:00'),
    (2, 1, 5, 'DEMAND_DEPOSIT', 'SAVINGS', 3.2500, 1000000, 15, DATE '2026-10-15', DATE '2020-04-15', DATE '2027-04-15', TIMESTAMP '2020-04-15 00:00:00'),
    (3, 1, 5, 'DEMAND_DEPOSIT', 'SAVINGS', 2.8000, 100000, 15, DATE '2026-10-15', DATE '2026-07-15', NULL, TIMESTAMP '2026-07-15 00:00:00'),
    (6, 2, NULL, NULL, 'DEPOSIT', 3.0000, NULL, NULL, NULL, DATE '2026-01-01', DATE '2027-01-01', TIMESTAMP '2026-01-01 00:00:00');
-- 계좌 2: 2020-04-15부터 2026-09-15까지 매월 15일 1,000,000원 PAID 78회. 잔액 78,000,000원과 일치해야 예상금액이 계산된다.
INSERT INTO savings_payment (deposit_saving_id, account_type, payment_date, amount, installment_number, status)
SELECT 2, 'SAVINGS', DATEADD('MONTH', r.n - 1, DATE '2020-04-15'), 1000000, r.n, 'PAID'
FROM SYSTEM_RANGE(1, 78) AS r(n);
INSERT INTO savings_payment (deposit_saving_id, account_type, payment_date, amount, installment_number, status)
VALUES
    (3, 'SAVINGS', DATE '2026-07-15', 100000, 1, 'PAID'),
    (3, 'SAVINGS', DATE '2026-08-15', 100000, 2, 'PAID');
