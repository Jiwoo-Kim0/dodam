-- S15P21D105-536: 전용 H2 DB의 결정적 fixture. 매 테스트 재적재하며 운영 seed가 아니다.
-- 기준일 2026-09-09 기준으로 다음을 만든다.
--   계좌 1  진행 중 예금(원금 1,000만, 계약 3.15%, 2026-01-01 ~ 2027-01-01, KB Star 정기예금 연결)
--   계좌 2  상세행 없는 예금(목록에는 보이지만 금리·기간을 모르는 상태)
--   계좌 3  해지된 예금(잔액 0)
--   계좌 4  만기가 지났으나 미인출인 예금(2024-09-01 ~ 2026-09-01, 계약 3.05%)
--   계좌 5  같은 사용자의 적금(예금 API에서는 404여야 한다)
--   계좌 6  타인의 예금(404여야 한다)
--   계좌 7  연금예금 연결 예금(비교군 표본 부족 경로)
--   계좌 8  만기일이 없는 예금(만기 예상 불가, 중도해지는 가능)
-- 계좌번호 해시는 테스트가 SensitiveNumberHasher로 다시 채운다. 여기서는 UNIQUE 충족용 자리값만 둔다.
--
-- H2 스키마는 account를 부분 매핑한 엔티티에서 만들어져 조회에 필요한 컬럼이 빠져 있다.
-- 535 fixture와 같은 이유로 없는 컬럼만 보완한다. MySQL 제약 검증은 이 테스트의 범위가 아니다.
ALTER TABLE account ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE account ADD COLUMN IF NOT EXISTS balance BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS account_no_hash VARCHAR(64);

DELETE FROM savings_payment;
DELETE FROM deposit_saving;
DELETE FROM account;
DELETE FROM financial_product_option;
DELETE FROM financial_product;
DELETE FROM financial_company;

INSERT INTO financial_company (company_id, company_code, company_name, sector) VALUES
    (1, 'KB_KOOKMIN', 'KB국민은행', 'BANK'),
    (2, 'SHINHAN_BANK', '신한은행', 'BANK'),
    (3, 'KAKAOBANK', '카카오뱅크', 'BANK'),
    (4, 'WOORI_BANK', '우리은행', 'BANK'),
    (5, 'DODAM_SB', '도담저축은행', 'SAVINGS_BANK'),
    -- 업권이 없는 회사. 보유 업권을 알 수 없는 경로를 만든다.
    (6, 'NO_SECTOR_BANK', '업권미상은행', NULL);

INSERT INTO financial_product
    (product_id, company_id, source_type, product_type, product_name, saving_type, synced_at)
VALUES
    (1, 1, 'SEED', 'DEPOSIT', 'KB Star 정기예금', 'FIXED_DEPOSIT', TIMESTAMP '2026-09-03 13:30:00'),
    (2, 2, 'SEED', 'DEPOSIT', '쏠편한 정기예금', 'FIXED_DEPOSIT', TIMESTAMP '2026-09-01 09:00:00'),
    (3, 3, 'SEED', 'DEPOSIT', '정기예금', 'FIXED_DEPOSIT', TIMESTAMP '2026-09-05 09:00:00'),
    (4, 4, 'SEED', 'DEPOSIT', 'WON플러스 예금', 'FIXED_DEPOSIT', TIMESTAMP '2026-09-04 09:00:00'),
    (5, 1, 'SEED', 'DEPOSIT', '미래설계 연금예금', 'PENSION_DEPOSIT', TIMESTAMP '2026-09-02 09:00:00'),
    (6, 5, 'SEED', 'DEPOSIT', '도담 정기예금', 'FIXED_DEPOSIT', TIMESTAMP '2026-09-06 09:00:00'),
    -- saving_type이 비어 비교군을 만들 수 없는 상품.
    (7, 6, 'SEED', 'DEPOSIT', '유형미상 예금', NULL, TIMESTAMP '2026-09-06 09:00:00'),
    -- 예금 비교 모집단에 섞이면 안 되는 대출 상품.
    (8, 1, 'SEED', 'LOAN', 'KB 신용대출', NULL, TIMESTAMP '2026-09-06 09:00:00');

INSERT INTO financial_product_option
    (product_option_id, product_id, term, rate, preferred_rate, rate_type)
VALUES
    (1, 1, 12, 2.9000, NULL, 'FIXED'),
    (2, 1, 24, 2.8000, NULL, 'FIXED'),
    (3, 2, 12, 3.0000, NULL, NULL),
    (4, 2, 24, 2.8500, NULL, NULL),
    (5, 3, 12, 3.2000, 3.4000, 'FIXED'),
    (6, 4, 12, 3.1000, NULL, NULL),
    (7, 4, 24, 2.9500, NULL, NULL),
    (8, 5, 12, 2.5000, NULL, 'FIXED'),
    (9, 6, 12, 3.8000, NULL, NULL),
    (10, 7, 12, 3.0000, NULL, NULL),
    (11, 8, 12, 5.0000, NULL, NULL);

INSERT INTO account
    (account_id, user_id, bank_code, bank_name, account_no_masked, account_no_hash,
     account_name, account_type, status, balance, currency, created_at, updated_at)
VALUES
    (1, 1, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0001', 'placeholder-hash-1', '진행 예금', 'DEPOSIT', 'ACTIVE', 10000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
    (2, 1, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0002', 'placeholder-hash-2', '상세 누락 예금', 'DEPOSIT', 'ACTIVE', 5000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
    (3, 1, 'SHINHAN_BANK', '신한은행', '110-***-**0003', 'placeholder-hash-3', '해지 예금', 'DEPOSIT', 'CLOSED', 0, 'KRW', TIMESTAMP '2025-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
    (4, 1, 'SHINHAN_BANK', '신한은행', '110-***-**0004', 'placeholder-hash-4', '만기 예금', 'DEPOSIT', 'INACTIVE', 20000000, 'KRW', TIMESTAMP '2024-09-01 00:00:00', TIMESTAMP '2026-09-01 00:00:00'),
    (5, 1, 'KAKAOBANK', '카카오뱅크', '110-***-**0005', 'placeholder-hash-5', '적금', 'SAVINGS', 'ACTIVE', 600000, 'KRW', TIMESTAMP '2026-02-01 00:00:00', TIMESTAMP '2026-02-01 00:00:00'),
    (6, 2, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0006', 'placeholder-hash-6', '타인 예금', 'DEPOSIT', 'ACTIVE', 7000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
    (7, 1, 'KB_KOOKMIN', 'KB국민은행', '110-***-**0007', 'placeholder-hash-7', '연금 예금', 'DEPOSIT', 'ACTIVE', 3000000, 'KRW', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00'),
    (8, 1, 'SHINHAN_BANK', '신한은행', '110-***-**0008', 'placeholder-hash-8', '만기일 없는 예금', 'DEPOSIT', 'ACTIVE', 1000000, 'KRW', TIMESTAMP '2026-03-01 00:00:00', TIMESTAMP '2026-03-01 00:00:00');

INSERT INTO deposit_saving
    (account_id, user_id, account_type, financial_product_id, interest_rate,
     opening_date, expiry_date, created_at)
VALUES
    (1, 1, 'DEPOSIT', 1, 3.1500, DATE '2026-01-01', DATE '2027-01-01', TIMESTAMP '2026-01-01 00:00:00'),
    (3, 1, 'DEPOSIT', 2, 3.0000, DATE '2025-01-01', DATE '2026-01-01', TIMESTAMP '2025-01-01 00:00:00'),
    (4, 1, 'DEPOSIT', 2, 3.0500, DATE '2024-09-01', DATE '2026-09-01', TIMESTAMP '2024-09-01 00:00:00'),
    (6, 2, 'DEPOSIT', 1, 3.1500, DATE '2026-01-01', DATE '2027-01-01', TIMESTAMP '2026-01-01 00:00:00'),
    (7, 1, 'DEPOSIT', 5, 2.6000, DATE '2026-01-01', DATE '2027-01-01', TIMESTAMP '2026-01-01 00:00:00'),
    (8, 1, 'DEPOSIT', 2, 3.0000, DATE '2026-03-01', NULL, TIMESTAMP '2026-03-01 00:00:00');

INSERT INTO deposit_saving
    (account_id, user_id, account_type, financial_product_id, interest_rate, monthly_amount,
     opening_date, expiry_date, created_at)
VALUES
    (5, 1, 'SAVINGS', NULL, 4.0000, 100000, DATE '2026-02-01', DATE '2027-02-01', TIMESTAMP '2026-02-01 00:00:00');
