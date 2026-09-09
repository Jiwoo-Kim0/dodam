-- S15P21D105-530: 격리된 H2 테스트 DB에서 카드 fixture 뒤에 실행한다.
-- 각 클래스 최초 실행 시 빈 테이블을 만들며 실제 MySQL 마이그레이션을 대체하지 않는다.
CREATE TABLE installment (
    installment_id BIGINT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    total_amount BIGINT NOT NULL CHECK (total_amount > 0),
    months INT NOT NULL CHECK (months > 0),
    remaining_principal BIGINT CHECK (remaining_principal IS NULL OR remaining_principal >= 0),
    remaining_months INT NOT NULL CHECK (remaining_months BETWEEN 0 AND months),
    monthly_amount BIGINT NOT NULL CHECK (monthly_amount > 0),
    fee_rate DECIMAL(10,4) CHECK (fee_rate IS NULL OR fee_rate >= 0),
    purchase_date DATE NOT NULL,
    first_payment_date DATE NOT NULL,
    next_payment_date DATE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELED', 'OVERDUE')),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CHECK (purchase_date <= first_payment_date),
    FOREIGN KEY (card_id) REFERENCES card(card_id)
);
