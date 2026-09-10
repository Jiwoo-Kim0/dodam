-- S15P21D105-537: 전용 H2 DB의 결정적 fixture. 매 테스트 재적재하며 운영 seed가 아니다.
-- 기준일 2026-09-09 기준으로 다음을 만든다.
--   계좌 1  진행 중 적금(월 300,000 / 10일 / 3.6% / 2026-07-10 ~ 2027-07-10, 2회 납입 완료)
--   계좌 2  진행 중 적금(월 500,000 / 15일 / 3.4% / 2026-08-15 ~ 2027-08-15, 1회 납입 완료)
--   계좌 3  상세행 없는 적금
--   계좌 4  해지된 적금(잔액 0인데 납입 이력은 남아 있다)
--   계좌 5  출금계좌(DEMAND_DEPOSIT). 적금 API에서는 404여야 한다
--   계좌 6  예금(DEPOSIT). 적금 API에서는 404여야 한다
--   계좌 7  타인의 적금
-- 계좌번호 해시는 테스트가 SensitiveNumberHasher로 다시 채운다. 여기서는 UNIQUE 충족용 자리값만 둔다.
--
-- H2 스키마는 account를 부분 매핑한 엔티티에서 만들어져 조회에 필요한 컬럼이 빠질 수 있다.
-- 535·536 fixture와 같은 이유로 없는 컬럼만 보완하고 NOT NULL 컬럼은 모두 채운다.
ALTER TABLE account ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE account ADD COLUMN IF NOT EXISTS balance BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS account_no_hash VARCHAR(64);

DELETE FROM savings_payment;
DELETE FROM deposit_saving;
DELETE FROM account;
DELETE FROM financial_profile;
DELETE FROM financial_product_option;
DELETE FROM financial_product;
DELETE FROM financial_company;

INSERT INTO financial_profile (user_id, monthly_income, fixed_expense, updated_at) VALUES
    (1, 3000000, 800000, TIMESTAMP '2026-09-09 00:00:00'),
    (2, 9000000, 1000000, TIMESTAMP '2026-09-09 00:00:00');

INSERT INTO financial_company (company_id, company_code, company_name, sector) VALUES
    (1, 'KB_KOOKMIN', 'KB국민은행', 'BANK'),
    (2, 'SHINHAN_BANK', '신한은행', 'BANK'),
    (3, 'KAKAOBANK', '카카오뱅크', 'BANK'),
    (4, 'WOORI_BANK', '우리은행', 'BANK'),
    (5, 'DODAM_SB', '도담저축은행', 'SAVINGS_BANK'),
    -- 업권이 없는 회사. 보유 업권을 알 수 없는 경로를 만든다.
    (6, 'NO_SECTOR_BANK', '업권미상은행', NULL);

-- 비교군 판정은 saving_type 키워드 우선순위를 따른다.
-- FLEXIBLE 4개(1~4), REGULAR 2개(5~6), RESTRICTED 1개(7), 대출 1개(8).
INSERT INTO financial_product
    (product_id, company_id, source_type, product_type, product_name, saving_type, synced_at)
VALUES
    (1, 1, 'SEED', 'SAVINGS', 'KB내맘대로적금', 'FLEXIBLE', TIMESTAMP '2026-09-03 13:30:00'),
    (2, 2, 'SEED', 'SAVINGS', '신한 자유적금', 'FLEXIBLE', TIMESTAMP '2026-09-01 09:00:00'),
    (3, 3, 'SEED', 'SAVINGS', '카카오 자유적금', 'FLEXIBLE_PROMOTION', TIMESTAMP '2026-09-05 09:00:00'),
    (4, 4, 'SEED', 'SAVINGS', '우리 자유적금', 'GROUP_FLEXIBLE', TIMESTAMP '2026-09-04 09:00:00'),
    (5, 1, 'SEED', 'SAVINGS', 'KB 정액적금', 'REGULAR', TIMESTAMP '2026-09-02 09:00:00'),
    (6, 5, 'SEED', 'SAVINGS', '도담 정액적금', 'DIGITAL_REGULAR', TIMESTAMP '2026-09-06 09:00:00'),
    (7, 2, 'SEED', 'SAVINGS', '신한 청년미래적금', 'YOUTH_POLICY_REGULAR', TIMESTAMP '2026-09-06 09:00:00'),
    -- 적금 비교 모집단에 섞이면 안 되는 대출 상품.
    (8, 1, 'SEED', 'LOAN', 'KB 신용대출', NULL, TIMESTAMP '2026-09-06 09:00:00'),
    -- saving_type이 비어 비교군을 만들 수 없는 상품.
    (9, 6, 'SEED', 'SAVINGS', '유형미상 적금', NULL, TIMESTAMP '2026-09-06 09:00:00');

INSERT INTO financial_product_option
    (product_option_id, product_id, term, rate, preferred_rate, rate_type)
VALUES
    (1, 1, 12, 3.0000, 5.0000, 'FIXED'),
    (2, 1, 24, 2.9000, NULL, 'FIXED'),
    (3, 2, 12, 3.2000, NULL, NULL),
    (4, 2, 24, 2.9500, NULL, NULL),
    (5, 3, 12, 3.4000, 8.0000, NULL),
    (6, 4, 12, 3.6000, NULL, NULL),
    (7, 5, 12, 2.7000, NULL, NULL),
    (8, 6, 12, 4.1000, NULL, NULL),
    (9, 7, 12, 5.5000, 10.0000, NULL),
    (10, 8, 12, 6.0000, NULL, NULL),
    (11, 9, 12, 3.0000, NULL, NULL);

INSERT INTO account
    (account_id, user_id, bank_code, bank_name, account_no_masked, account_no_hash,
     account_name, account_type, status, balance, currency, created_at, updated_at)
VALUES
    (1, 1, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0001', 'placeholder-hash-1', '진행 적금', 'SAVINGS', 'ACTIVE', 600000, 'KRW', TIMESTAMP '2026-07-10 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (2, 1, 'SHINHAN_BANK', '신한은행', '110-***-**0002', 'placeholder-hash-2', '두번째 적금', 'SAVINGS', 'ACTIVE', 500000, 'KRW', TIMESTAMP '2026-08-15 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (3, 1, 'KAKAOBANK', '카카오뱅크', '110-***-**0003', 'placeholder-hash-3', '상세 누락 적금', 'SAVINGS', 'ACTIVE', 0, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (4, 1, 'WOORI_BANK', '우리은행', '110-***-**0004', 'placeholder-hash-4', '해지 적금', 'SAVINGS', 'CLOSED', 0, 'KRW', TIMESTAMP '2026-01-20 00:00:00', TIMESTAMP '2026-07-20 00:00:00'),
    (5, 1, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0005', 'placeholder-hash-5', '출금계좌', 'DEMAND_DEPOSIT', 'ACTIVE', 5000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (6, 1, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0006', 'placeholder-hash-6', '예금', 'DEPOSIT', 'ACTIVE', 10000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-09-09 00:00:00'),
    (7, 2, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0007', 'placeholder-hash-7', '타인 적금', 'SAVINGS', 'ACTIVE', 300000, 'KRW', TIMESTAMP '2026-08-10 00:00:00', TIMESTAMP '2026-09-09 00:00:00');

INSERT INTO deposit_saving
    (account_id, user_id, withdrawal_account_id, withdrawal_account_type, account_type,
     financial_product_id, interest_rate, monthly_amount, payment_day, next_payment_date,
     opening_date, expiry_date, created_at)
VALUES
    (1, 1, 5, 'DEMAND_DEPOSIT', 'SAVINGS', 1, 3.6000, 300000, 10, DATE '2026-09-10', DATE '2026-07-10', DATE '2027-07-10', TIMESTAMP '2026-07-10 00:00:00'),
    (2, 1, 5, 'DEMAND_DEPOSIT', 'SAVINGS', 2, 3.4000, 500000, 15, DATE '2026-09-15', DATE '2026-08-15', DATE '2027-08-15', TIMESTAMP '2026-08-15 00:00:00'),
    (4, 1, 5, 'DEMAND_DEPOSIT', 'SAVINGS', 1, 3.8000, 200000, 20, DATE '2026-07-20', DATE '2026-01-20', DATE '2027-01-20', TIMESTAMP '2026-01-20 00:00:00'),
    (7, 2, NULL, NULL, 'SAVINGS', 1, 3.5000, 300000, 10, DATE '2026-09-10', DATE '2026-08-10', DATE '2027-08-10', TIMESTAMP '2026-08-10 00:00:00');

INSERT INTO deposit_saving
    (account_id, user_id, account_type, financial_product_id, interest_rate, opening_date, expiry_date, created_at)
VALUES
    (6, 1, 'DEPOSIT', NULL, 3.1500, DATE '2026-01-01', DATE '2027-01-01', TIMESTAMP '2026-01-01 00:00:00');

INSERT INTO savings_payment
    (savings_payment_id, deposit_saving_id, account_type, payment_date, amount, installment_number, status)
VALUES
    (1, 1, 'SAVINGS', DATE '2026-07-10', 300000, 1, 'PAID'),
    (2, 1, 'SAVINGS', DATE '2026-08-10', 300000, 2, 'PAID'),
    -- 기준일 이후의 저장된 예정 회차. 같은 달에 약정액을 중복으로 더하지 않는지 확인한다.
    (3, 1, 'SAVINGS', DATE '2026-09-10', 300000, 3, 'SCHEDULED'),
    (4, 2, 'SAVINGS', DATE '2026-08-15', 500000, 1, 'PAID'),
    -- 해지 계좌의 과거 실적. 잔액 0과 어긋나는 상태를 만든다.
    (5, 4, 'SAVINGS', DATE '2026-01-20', 200000, 1, 'PAID'),
    (6, 4, 'SAVINGS', DATE '2026-02-20', 200000, 2, 'PAID'),
    (7, 7, 'SAVINGS', DATE '2026-08-10', 300000, 1, 'PAID');

-- 현금흐름 부담용 대출 상환. 낸 금액과 남은 예정액을 분리해 합산하는지 확인한다.
--
-- loan·loan_repayment에는 JPA 엔티티(loan_repayment)가 없어 H2가 테이블을 만들어 주지 않는다.
-- 531 대출 fixture와 같은 방식으로 이 테스트가 쓰는 컬럼만 최소로 만든다. 실제 MySQL 마이그레이션(V13)을
-- 대체하지 않으며 제약조건 검증도 대신하지 않는다.
DROP TABLE IF EXISTS loan_repayment;
DROP TABLE IF EXISTS loan;
CREATE TABLE loan (
    loan_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL
);
CREATE TABLE loan_repayment (
    loan_repayment_id BIGINT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    repayment_date DATE NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('SCHEDULED', 'PAID', 'FAILED', 'OVERDUE'))
);

INSERT INTO loan (loan_id, user_id) VALUES (1, 1);

INSERT INTO loan_repayment (loan_repayment_id, loan_id, repayment_date, amount, status) VALUES
    (1, 1, DATE '2026-09-05', 300000, 'PAID'),
    (2, 1, DATE '2026-09-25', 100000, 'SCHEDULED'),
    -- 실패 회차는 낸 금액에도 남은 예정액에도 들어가지 않아야 한다.
    (3, 1, DATE '2026-09-06', 999000, 'FAILED');
