-- S15P21D105-535: 전용 H2 DB의 결정적 fixture. 매 테스트 재적재하며 운영 seed가 아니다.
-- 기준일 2026-09-09: 사용자 1 보유잔액 1,850,000원, 8월 PAID 600,000원 / 소득 3,000,000원 = 20%.
-- H2의 부분 Account 엔티티 매핑에 없는 조회 컬럼만 보완한다. MySQL FK 검증은 별도 테스트다.
ALTER TABLE account ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE account ADD COLUMN IF NOT EXISTS balance BIGINT;
DELETE FROM savings_payment;
DELETE FROM deposit_saving;
DELETE FROM account;
DELETE FROM financial_profile;
INSERT INTO financial_profile (user_id, monthly_income, updated_at)
VALUES (1, 3000000, TIMESTAMP '2026-09-09 00:00:00'),
       (2, 9000000, TIMESTAMP '2026-09-09 00:00:00');
-- H2 스키마는 account를 부분 매핑한 엔티티에서 만들어지므로 NOT NULL 컬럼을 모두 채운다.
-- currency·account_no_hash·created_at·updated_at을 빼면 INSERT가 NOT NULL 위반으로 실패한다.
-- 계좌번호 해시는 UNIQUE 충족용 자리값이며 실제 민감번호가 아니다.
INSERT INTO account
    (account_id, user_id, bank_code, bank_name, account_no_masked, account_no_hash,
     account_name, account_type, status, balance, currency, created_at, updated_at)
VALUES
    (1, 1, 'TEST', '시연은행', '***-01', 'placeholder-hash-1', '예금', 'DEPOSIT', 'ACTIVE', 1000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (2, 1, 'TEST', '시연은행', '***-02', 'placeholder-hash-2', '적금', 'SAVINGS', 'ACTIVE', 600000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (3, 1, 'TEST', '시연은행', '***-03', 'placeholder-hash-3', '종료 적금', 'SAVINGS', 'CLOSED', 0, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (4, 1, 'TEST', '시연은행', '***-04', 'placeholder-hash-4', '만기 예금', 'DEPOSIT', 'INACTIVE', 200000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (5, 1, 'TEST', '시연은행', '***-05', 'placeholder-hash-5', '상세 누락 예금', 'DEPOSIT', 'ACTIVE', 50000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (6, 2, 'TEST', '시연은행', '***-06', 'placeholder-hash-6', '타인 적금', 'SAVINGS', 'ACTIVE', 9000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00');
INSERT INTO deposit_saving
    (account_id, user_id, account_type, interest_rate, monthly_amount, opening_date, expiry_date, created_at)
VALUES
    (1, 1, 'DEPOSIT', 3.5, NULL, DATE '2026-01-01', DATE '2026-10-09', TIMESTAMP '2026-01-01 00:00:00'),
    (2, 1, 'SAVINGS', 4.0, 100000, DATE '2026-01-01', DATE '2026-09-10', TIMESTAMP '2026-01-01 00:00:00'),
    (3, 1, 'SAVINGS', 4.0, 100000, DATE '2026-01-01', DATE '2026-08-31', TIMESTAMP '2026-01-01 00:00:00'),
    (4, 1, 'DEPOSIT', 3.0, NULL, DATE '2026-01-01', DATE '2026-09-09', TIMESTAMP '2026-01-01 00:00:00'),
    (6, 2, 'SAVINGS', 4.0, 9000000, DATE '2026-01-01', DATE '2026-09-10', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO savings_payment
    (deposit_saving_id, account_type, payment_date, amount, installment_number, status)
VALUES
    (2, 'SAVINGS', DATE '2026-08-01', 300000, 1, 'PAID'),
    (2, 'SAVINGS', DATE '2026-08-31', 200000, 2, 'PAID'),
    (3, 'SAVINGS', DATE '2026-08-15', 100000, 1, 'PAID'),
    (2, 'SAVINGS', DATE '2026-09-09', 100000, 3, 'PAID'),
    (2, 'SAVINGS', DATE '2026-09-10', 80000, 4, 'PAID'),
    (2, 'SAVINGS', DATE '2026-08-02', 700000, 5, 'SCHEDULED'),
    (2, 'SAVINGS', DATE '2026-08-03', 700000, 6, 'FAILED'),
    (2, 'SAVINGS', DATE '2026-08-04', 700000, 7, 'SKIPPED'),
    (6, 'SAVINGS', DATE '2026-08-01', 9000000, 1, 'PAID');
