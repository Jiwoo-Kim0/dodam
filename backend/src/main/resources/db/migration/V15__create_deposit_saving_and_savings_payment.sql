-- S15P21D105-535: 예적금 공통 조회와 납입률에 필요한 ERD 테이블. V14는 534 마이너스통장용으로 유지한다.
CREATE TABLE `deposit_saving` (
  `account_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NULL,
  `withdrawal_account_type` VARCHAR(30) NULL,
  `financial_product_id` BIGINT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `monthly_amount` BIGINT NULL,
  `payment_day` INT NULL,
  `next_payment_date` DATE NULL,
  `opening_date` DATE NOT NULL,
  `expiry_date` DATE NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_deposit_saving_user_account` (`user_id`, `account_id`),
  UNIQUE KEY `uk_deposit_saving_account_type` (`account_id`, `account_type`),
  KEY `idx_deposit_saving_parent` (`user_id`, `account_id`, `account_type`),
  KEY `idx_deposit_saving_financial_product_id` (`financial_product_id`),
  KEY `idx_deposit_saving_user_withdrawal_account`
    (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`),
  CONSTRAINT `chk_deposit_saving_account_type`
    CHECK (`account_type` IN ('DEPOSIT', 'SAVINGS')),
  CONSTRAINT `chk_deposit_saving_withdrawal_account`
    CHECK ((`withdrawal_account_id` IS NULL AND `withdrawal_account_type` IS NULL
            AND `account_type` = 'DEPOSIT')
        -- MySQL CHECK는 UNKNOWN도 허용한다. 유형 NULL로 복합 FK를 우회하지 못하게 한다.
        OR (`withdrawal_account_id` IS NOT NULL AND `withdrawal_account_type` IS NOT NULL
            AND `withdrawal_account_type` = 'DEMAND_DEPOSIT')),
  CONSTRAINT `chk_deposit_saving_interest_rate`
    CHECK (`interest_rate` >= 0),
  CONSTRAINT `chk_deposit_saving_monthly_amount`
    CHECK (`monthly_amount` IS NULL OR `monthly_amount` > 0),
  CONSTRAINT `chk_deposit_saving_payment_day`
    CHECK (`payment_day` IS NULL OR `payment_day` BETWEEN 1 AND 31),
  CONSTRAINT `chk_deposit_saving_dates`
    CHECK (`expiry_date` IS NULL OR `opening_date` <= `expiry_date`),
  CONSTRAINT `fk_deposit_saving_account`
    FOREIGN KEY (`user_id`, `account_id`, `account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`),
  CONSTRAINT `fk_deposit_saving_financial_product`
    FOREIGN KEY (`financial_product_id`)
    REFERENCES `financial_product` (`product_id`),
  CONSTRAINT `fk_deposit_saving_withdrawal_account`
    FOREIGN KEY (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

CREATE TABLE `savings_payment` (
  `savings_payment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `deposit_saving_id` BIGINT NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `payment_date` DATE NOT NULL,
  `amount` BIGINT NOT NULL,
  `installment_number` INT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  PRIMARY KEY (`savings_payment_id`),
  KEY `idx_savings_payment_deposit_saving_id` (`deposit_saving_id`),
  KEY `idx_savings_payment_parent` (`deposit_saving_id`, `account_type`),
  UNIQUE KEY `uk_savings_payment_installment`
    (`deposit_saving_id`, `installment_number`),
  CONSTRAINT `chk_savings_payment_account_type`
    CHECK (`account_type` = 'SAVINGS'),
  CONSTRAINT `chk_savings_payment_status`
    CHECK (`status` IN ('SCHEDULED', 'PAID', 'FAILED', 'SKIPPED')),
  CONSTRAINT `chk_savings_payment_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `chk_savings_payment_installment_number`
    CHECK (`installment_number` > 0),
  CONSTRAINT `fk_savings_payment_deposit_saving`
    FOREIGN KEY (`deposit_saving_id`, `account_type`)
    REFERENCES `deposit_saving` (`account_id`, `account_type`)
);
