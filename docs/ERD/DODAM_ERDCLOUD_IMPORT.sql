-- DODAM ERDCloud Import DDL
-- Dialect: MySQL
-- Revision: 2026-09-07 exhaustive domain/API/Seed consistency pass
-- Basis: planning document + API specifications 1-22 + relationship/cardinality table
-- Import in ERDCloud via Import > DDL / SQL.
-- This is a fresh-schema import DDL. Existing databases require a separate migration.

CREATE TABLE `user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `password_hash` VARCHAR(255) NULL,
  `name` VARCHAR(100) NOT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_email` (`email`),
  CONSTRAINT `chk_user_role`
    CHECK (`role` IN ('USER', 'ADMIN'))
);

CREATE TABLE `user_setting` (
  `user_id` BIGINT NOT NULL,
  `notification_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `daily_briefing_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `monthly_report_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `daily_briefing_time` TIME NULL,
  `timezone` VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `chk_user_setting_flags`
    CHECK (`notification_enabled` IN (0, 1)
       AND `daily_briefing_enabled` IN (0, 1)
       AND `monthly_report_enabled` IN (0, 1)),
  CONSTRAINT `fk_user_setting_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `financial_profile` (
  `user_id` BIGINT NOT NULL,
  `monthly_income` BIGINT NULL,
  `annual_income` BIGINT NULL,
  `payday` INT NULL,
  `fixed_expense` BIGINT NULL,
  `emergency_fund_target` BIGINT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `chk_financial_profile_monthly_income`
    CHECK (`monthly_income` IS NULL OR `monthly_income` >= 0),
  CONSTRAINT `chk_financial_profile_annual_income`
    CHECK (`annual_income` IS NULL OR `annual_income` >= 0),
  CONSTRAINT `chk_financial_profile_payday`
    CHECK (`payday` IS NULL OR `payday` BETWEEN 1 AND 31),
  CONSTRAINT `chk_financial_profile_fixed_expense`
    CHECK (`fixed_expense` IS NULL OR `fixed_expense` >= 0),
  CONSTRAINT `chk_financial_profile_emergency_fund_target`
    CHECK (`emergency_fund_target` IS NULL OR `emergency_fund_target` >= 0),
  CONSTRAINT `fk_financial_profile_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `user_oauth_account` (
  `oauth_account_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `provider` VARCHAR(30) NOT NULL,
  `provider_user_id_hash` CHAR(64) NOT NULL,
  `provider_email_encrypted` VARBINARY(512) NULL,
  `provider_email_hash` CHAR(64) NULL,
  `provider_email_masked` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`oauth_account_id`),
  UNIQUE KEY `uk_user_oauth_provider_user` (`provider`, `provider_user_id_hash`),
  UNIQUE KEY `uk_user_oauth_user_provider` (`user_id`, `provider`),
  CONSTRAINT `fk_user_oauth_account_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `user_device` (
  `device_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `fcm_token_encrypted` VARBINARY(1024) NOT NULL,
  `fcm_token_hash` CHAR(64) NOT NULL,
  `platform` VARCHAR(20) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `last_used_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `uk_user_device_fcm_token_hash` (`fcm_token_hash`),
  KEY `idx_user_device_user_id` (`user_id`),
  CONSTRAINT `chk_user_device_enabled`
    CHECK (`enabled` IN (0, 1)),
  CONSTRAINT `chk_user_device_platform`
    CHECK (`platform` IN ('ANDROID', 'IOS', 'WEB')),
  CONSTRAINT `fk_user_device_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `auth_session` (
  `session_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `refresh_token_hash` VARCHAR(255) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `created_at` DATETIME NOT NULL,
  `last_accessed_at` DATETIME NULL,
  `revoked_at` DATETIME NULL,
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_auth_session_refresh_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_session_user_id` (`user_id`),
  CONSTRAINT `fk_auth_session_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `pattern_analysis` (
  `analysis_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `lookback_months` INT NOT NULL,
  `data_point_count` INT NOT NULL,
  `spending_regularity` DECIMAL(10,4) NULL,
  `saving_consistency` DECIMAL(10,4) NULL,
  `fixed_expense_ratio` DECIMAL(10,4) NULL,
  `variable_expense_ratio` DECIMAL(10,4) NULL,
  `discretionary_spending_ratio` DECIMAL(10,4) NULL,
  `debt_burden` DECIMAL(10,4) NULL,
  `cashflow_stability` DECIMAL(10,4) NULL,
  `liquidity_level` VARCHAR(30) NULL,
  `model_version` VARCHAR(50) NULL,
  `cluster_id` INT NULL,
  `cluster_label` VARCHAR(50) NULL,
  `feature_window_months` INT NULL,
  `feature_summary_json` JSON NULL,
  `cluster_stats_json` JSON NULL,
  `sample_size` INT NULL,
  `confidence` DECIMAL(5,4) NULL,
  `analyzed_at` DATETIME NOT NULL,
  PRIMARY KEY (`analysis_id`),
  KEY `idx_pattern_analysis_user_id` (`user_id`),
  CONSTRAINT `chk_pattern_analysis_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `chk_pattern_analysis_lookback_months`
    CHECK (`lookback_months` > 0),
  CONSTRAINT `chk_pattern_analysis_data_point_count`
    CHECK (`data_point_count` >= 0),
  CONSTRAINT `chk_pattern_analysis_feature_window_months`
    CHECK (`feature_window_months` IS NULL OR `feature_window_months` > 0),
  CONSTRAINT `chk_pattern_analysis_sample_size`
    CHECK (`sample_size` IS NULL OR `sample_size` >= 0),
  CONSTRAINT `chk_pattern_analysis_confidence`
    CHECK (`confidence` IS NULL OR `confidence` BETWEEN 0 AND 1),
  CONSTRAINT `fk_pattern_analysis_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `finance_connection` (
  `connection_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `member_email_encrypted` VARBINARY(512) NULL,
  `member_email_hash` CHAR(64) NULL,
  `member_email_masked` VARCHAR(255) NULL,
  `external_user_key_encrypted` VARBINARY(512) NULL,
  `external_user_key_hash` CHAR(64) NULL,
  `status` VARCHAR(30) NOT NULL,
  `connected_at` DATETIME NULL,
  `disconnected_at` DATETIME NULL,
  `last_synced_at` DATETIME NULL,
  PRIMARY KEY (`connection_id`),
  UNIQUE KEY `uk_finance_connection_user_id` (`user_id`),
  UNIQUE KEY `uk_finance_connection_member_email_hash` (`member_email_hash`),
  UNIQUE KEY `uk_finance_connection_external_user_key_hash` (`external_user_key_hash`),
  CONSTRAINT `chk_finance_connection_status`
    CHECK (`status` IN ('PENDING', 'CONNECTED', 'DISCONNECTED', 'FAILED')),
  CONSTRAINT `fk_finance_connection_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `finance_sync` (
  `sync_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `scope` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `progress` INT NULL,
  `error_message` VARCHAR(1000) NULL,
  `started_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`sync_id`),
  KEY `idx_finance_sync_user_id` (`user_id`),
  CONSTRAINT `chk_finance_sync_scope`
    CHECK (`scope` IN ('ALL', 'ACCOUNT', 'CARD', 'LOAN', 'SAVING')),
  CONSTRAINT `chk_finance_sync_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `chk_finance_sync_progress`
    CHECK (`progress` IS NULL OR `progress` BETWEEN 0 AND 100),
  CONSTRAINT `fk_finance_sync_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `account` (
  `account_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `bank_code` VARCHAR(20) NOT NULL,
  `bank_name` VARCHAR(100) NOT NULL,
  `account_no_encrypted` VARBINARY(512) NOT NULL,
  `account_no_hash` CHAR(64) NOT NULL,
  `account_no_masked` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(100) NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  `balance` BIGINT NOT NULL,
  `last_transaction_date` DATE NULL,
  `currency` CHAR(3) NOT NULL DEFAULT 'KRW',
  `source_type` VARCHAR(20) NOT NULL DEFAULT 'SSAFY',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_account_user_account_hash` (`user_id`, `account_no_hash`),
  UNIQUE KEY `uk_account_user_account_id` (`user_id`, `account_id`),
  UNIQUE KEY `uk_account_user_id_type` (`user_id`, `account_id`, `account_type`),
  KEY `idx_account_user_bank` (`user_id`, `bank_code`),
  KEY `idx_account_user_type` (`user_id`, `account_type`),
  KEY `idx_account_user_inactive` (`user_id`, `last_transaction_date`, `balance`),
  CONSTRAINT `chk_account_type`
    CHECK (`account_type` IN ('DEMAND_DEPOSIT', 'MINUS', 'DEPOSIT', 'SAVINGS')),
  CONSTRAINT `chk_account_status`
    CHECK (`status` IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
  CONSTRAINT `chk_account_balance_by_type`
    CHECK ((`account_type` = 'MINUS' AND `balance` <= 0)
        OR (`account_type` <> 'MINUS' AND `balance` >= 0)),
  CONSTRAINT `chk_account_source_type`
    CHECK (`source_type` IN ('SSAFY', 'MANUAL', 'SEED')),
  CONSTRAINT `fk_account_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `transaction_category` (
  `category_id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `default_spending_type` VARCHAR(20) NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_transaction_category_code` (`code`),
  CONSTRAINT `chk_transaction_category_spending_type`
    CHECK (`default_spending_type` IS NULL OR `default_spending_type` IN ('FIXED', 'VARIABLE', 'NON_SPENDING')),
  CONSTRAINT `chk_transaction_category_active`
    CHECK (`active` IN (0, 1))
);

CREATE TABLE `financial_company` (
  `company_id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_code` VARCHAR(30) NOT NULL,
  `company_name` VARCHAR(150) NOT NULL,
  `sector` VARCHAR(50) NULL,
  PRIMARY KEY (`company_id`),
  UNIQUE KEY `uk_financial_company_code` (`company_code`)
);

CREATE TABLE `financial_product` (
  `product_id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `source_type` VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
  `external_product_code` VARCHAR(100) NULL,
  `product_type` VARCHAR(30) NOT NULL,
  `product_name` VARCHAR(255) NOT NULL,
  `join_way` VARCHAR(255) NULL,
  `saving_type` VARCHAR(30) NULL,
  `loan_type` VARCHAR(30) NULL,
  `special_condition` TEXT NULL,
  `join_member` TEXT NULL,
  `min_rate` DECIMAL(10,4) NULL,
  `max_rate` DECIMAL(10,4) NULL,
  `max_limit` BIGINT NULL,
  `baseline_performance` BIGINT NULL,
  `max_benefit_limit` BIGINT NULL,
  `description` TEXT NULL,
  `benefits` JSON NULL,
  `synced_at` DATETIME NOT NULL,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_financial_product_natural`
    (`company_id`, `product_type`, `product_name`),
  UNIQUE KEY `uk_financial_product_external`
    (`source_type`, `company_id`, `external_product_code`),
  KEY `idx_financial_product_company_id` (`company_id`),
  KEY `idx_financial_product_type_rate` (`product_type`, `min_rate`, `max_rate`),
  CONSTRAINT `chk_financial_product_type`
    CHECK (`product_type` IN ('DEPOSIT', 'SAVINGS', 'LOAN', 'CARD')),
  CONSTRAINT `chk_financial_product_source_type`
    CHECK (`source_type` IN ('FSS', 'ISSUER', 'MANUAL', 'SEED')),
  CONSTRAINT `chk_financial_product_external_code`
    CHECK ((`source_type` IN ('FSS', 'ISSUER') AND `external_product_code` IS NOT NULL)
        OR (`source_type` IN ('MANUAL', 'SEED'))),
  CONSTRAINT `chk_financial_product_rates`
    CHECK ((`min_rate` IS NULL OR `min_rate` >= 0)
       AND (`max_rate` IS NULL OR `max_rate` >= 0)
       AND (`min_rate` IS NULL OR `max_rate` IS NULL OR `min_rate` <= `max_rate`)),
  CONSTRAINT `chk_financial_product_limits`
    CHECK ((`max_limit` IS NULL OR `max_limit` >= 0)
       AND (`baseline_performance` IS NULL OR `baseline_performance` >= 0)
       AND (`max_benefit_limit` IS NULL OR `max_benefit_limit` >= 0)),
  CONSTRAINT `fk_financial_product_company`
    FOREIGN KEY (`company_id`) REFERENCES `financial_company` (`company_id`)
);

CREATE TABLE `financial_product_option` (
  `product_option_id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL,
  `term` INT NULL,
  `rate` DECIMAL(10,4) NULL,
  `preferred_rate` DECIMAL(10,4) NULL,
  `rate_type` VARCHAR(30) NULL,
  `min_rate` DECIMAL(10,4) NULL,
  `max_rate` DECIMAL(10,4) NULL,
  `repayment_type` VARCHAR(30) NULL,
  `limit_amount` BIGINT NULL,
  PRIMARY KEY (`product_option_id`),
  KEY `idx_financial_product_option_product_id` (`product_id`),
  CONSTRAINT `chk_financial_product_option_term`
    CHECK (`term` IS NULL OR `term` > 0),
  CONSTRAINT `chk_financial_product_option_rates`
    CHECK ((`rate` IS NULL OR `rate` >= 0)
       AND (`preferred_rate` IS NULL OR `preferred_rate` >= 0)
       AND (`min_rate` IS NULL OR `min_rate` >= 0)
       AND (`max_rate` IS NULL OR `max_rate` >= 0)
       AND (`min_rate` IS NULL OR `max_rate` IS NULL OR `min_rate` <= `max_rate`)),
  CONSTRAINT `chk_financial_product_option_limit_amount`
    CHECK (`limit_amount` IS NULL OR `limit_amount` >= 0),
  CONSTRAINT `fk_financial_product_option_product`
    FOREIGN KEY (`product_id`) REFERENCES `financial_product` (`product_id`)
);

CREATE TABLE `card` (
  `card_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `withdrawal_account_type` VARCHAR(30) NOT NULL,
  `financial_product_id` BIGINT NULL,
  `card_no_encrypted` VARBINARY(512) NOT NULL,
  `card_no_hash` CHAR(64) NOT NULL,
  `card_no_masked` VARCHAR(100) NOT NULL,
  `issuer_code` VARCHAR(30) NOT NULL,
  `issuer_name` VARCHAR(100) NOT NULL,
  `card_name` VARCHAR(150) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `credit_limit` BIGINT NOT NULL,
  `baseline_performance` BIGINT NULL,
  `max_benefit_limit` BIGINT NULL,
  `annual_fee` BIGINT NULL,
  `expiry_month` CHAR(7) NULL,
  `withdrawal_day` INT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`card_id`),
  UNIQUE KEY `uk_card_user_card_hash` (`user_id`, `card_no_hash`),
  UNIQUE KEY `uk_card_user_card_id` (`user_id`, `card_id`),
  KEY `idx_card_financial_product_id` (`financial_product_id`),
  KEY `idx_card_user_issuer` (`user_id`, `issuer_code`),
  KEY `idx_card_user_withdrawal_account`
    (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`),
  CONSTRAINT `chk_card_status`
    CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELED')),
  CONSTRAINT `chk_card_withdrawal_account_type`
    CHECK (`withdrawal_account_type` = 'DEMAND_DEPOSIT'),
  CONSTRAINT `chk_card_credit_limit`
    CHECK (`credit_limit` >= 0),
  CONSTRAINT `chk_card_baseline_performance`
    CHECK (`baseline_performance` IS NULL OR `baseline_performance` >= 0),
  CONSTRAINT `chk_card_max_benefit_limit`
    CHECK (`max_benefit_limit` IS NULL OR `max_benefit_limit` >= 0),
  CONSTRAINT `chk_card_annual_fee`
    CHECK (`annual_fee` IS NULL OR `annual_fee` >= 0),
  CONSTRAINT `chk_card_withdrawal_day`
    CHECK (`withdrawal_day` IS NULL OR `withdrawal_day` BETWEEN 1 AND 31),
  CONSTRAINT `fk_card_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_card_financial_product`
    FOREIGN KEY (`financial_product_id`)
    REFERENCES `financial_product` (`product_id`),
  CONSTRAINT `fk_card_withdrawal_account`
    FOREIGN KEY (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

CREATE TABLE `subscription` (
  `subscription_id` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `card_id` BIGINT NULL,
  `name` VARCHAR(150) NOT NULL,
  `payment_amount` BIGINT NOT NULL,
  `billing_cycle` VARCHAR(30) NOT NULL,
  `payment_day` INT NULL,
  `next_payment_date` DATE NULL,
  `end_date` DATE NULL,
  `status` VARCHAR(30) NOT NULL,
  `category` VARCHAR(50) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `canceled_at` DATETIME NULL,
  PRIMARY KEY (`subscription_id`),
  KEY `idx_subscription_user_id` (`user_id`),
  KEY `idx_subscription_user_card` (`user_id`, `card_id`),
  CONSTRAINT `chk_subscription_billing_cycle`
    CHECK (`billing_cycle` IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
  CONSTRAINT `chk_subscription_status`
    CHECK (`status` IN ('ACTIVE', 'PAUSED', 'CANCELED', 'EXPIRED')),
  CONSTRAINT `chk_subscription_payment_amount`
    CHECK (`payment_amount` > 0),
  CONSTRAINT `chk_subscription_payment_day`
    CHECK (`payment_day` IS NULL OR `payment_day` BETWEEN 1 AND 31),
  CONSTRAINT `chk_subscription_dates`
    CHECK (`end_date` IS NULL OR `next_payment_date` IS NULL OR `next_payment_date` <= `end_date`),
  CONSTRAINT `fk_subscription_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_subscription_card`
    FOREIGN KEY (`user_id`, `card_id`)
    REFERENCES `card` (`user_id`, `card_id`)
);

CREATE TABLE `subscription_payment_history` (
  `payment_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `subscription_id` VARCHAR(100) NOT NULL,
  `payment_date` DATE NOT NULL,
  `payment_amount` BIGINT NOT NULL,
  `result_code` VARCHAR(50) NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `result_message` VARCHAR(500) NULL,
  PRIMARY KEY (`payment_history_id`),
  KEY `idx_subscription_payment_history_subscription_id` (`subscription_id`),
  UNIQUE KEY `uk_subscription_payment_history_source`
    (`subscription_id`, `payment_date`, `payment_amount`),
  CONSTRAINT `chk_subscription_payment_history_amount`
    CHECK (`payment_amount` > 0),
  CONSTRAINT `chk_subscription_payment_history_retry_count`
    CHECK (`retry_count` >= 0),
  CONSTRAINT `fk_subscription_payment_history_subscription`
    FOREIGN KEY (`subscription_id`) REFERENCES `subscription` (`subscription_id`)
);

CREATE TABLE `card_benefit` (
  `card_benefit_id` BIGINT NOT NULL AUTO_INCREMENT,
  `card_id` BIGINT NOT NULL,
  `category` VARCHAR(50) NOT NULL,
  `discount_rate` DECIMAL(10,4) NULL,
  `limit_amount` BIGINT NULL,
  `description` VARCHAR(500) NOT NULL,
  PRIMARY KEY (`card_benefit_id`),
  KEY `idx_card_benefit_card_id` (`card_id`),
  CONSTRAINT `chk_card_benefit_discount_rate`
    CHECK (`discount_rate` IS NULL OR `discount_rate` BETWEEN 0 AND 100),
  CONSTRAINT `chk_card_benefit_limit_amount`
    CHECK (`limit_amount` IS NULL OR `limit_amount` >= 0),
  CONSTRAINT `fk_card_benefit_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
);

CREATE TABLE `card_billing_statement` (
  `card_id` BIGINT NOT NULL,
  `billing_month` CHAR(7) NOT NULL,
  `billing_date` DATE NOT NULL,
  `total_balance` BIGINT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `payment_date` DATE NULL,
  PRIMARY KEY (`card_id`, `billing_month`),
  CONSTRAINT `chk_card_billing_statement_status`
    CHECK (`status` IN ('SCHEDULED', 'PAID', 'OVERDUE', 'FAILED')),
  CONSTRAINT `chk_card_billing_statement_total_balance`
    CHECK (`total_balance` >= 0),
  CONSTRAINT `fk_card_billing_statement_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
);

CREATE TABLE `installment` (
  `installment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `card_id` BIGINT NOT NULL,
  `merchant_name` VARCHAR(255) NOT NULL,
  `total_amount` BIGINT NOT NULL,
  `months` INT NOT NULL,
  `remaining_principal` BIGINT NULL,
  `remaining_months` INT NOT NULL,
  `monthly_amount` BIGINT NOT NULL,
  `fee_rate` DECIMAL(10,4) NULL,
  `purchase_date` DATE NOT NULL,
  `first_payment_date` DATE NOT NULL,
  `next_payment_date` DATE NULL,
  `status` VARCHAR(30) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`installment_id`),
  KEY `idx_installment_card_id` (`card_id`),
  KEY `idx_installment_next_payment_date` (`next_payment_date`),
  CONSTRAINT `chk_installment_status`
    CHECK (`status` IN ('ACTIVE', 'COMPLETED', 'CANCELED', 'OVERDUE')),
  CONSTRAINT `chk_installment_total_amount`
    CHECK (`total_amount` > 0),
  CONSTRAINT `chk_installment_months`
    CHECK (`months` > 0),
  CONSTRAINT `chk_installment_remaining_principal`
    CHECK (`remaining_principal` IS NULL OR `remaining_principal` >= 0),
  CONSTRAINT `chk_installment_remaining_months`
    CHECK (`remaining_months` BETWEEN 0 AND `months`),
  CONSTRAINT `chk_installment_monthly_amount`
    CHECK (`monthly_amount` > 0),
  CONSTRAINT `chk_installment_fee_rate`
    CHECK (`fee_rate` IS NULL OR `fee_rate` >= 0),
  CONSTRAINT `chk_installment_payment_dates`
    CHECK (`purchase_date` <= `first_payment_date`),
  CONSTRAINT `fk_installment_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
);

CREATE TABLE `transaction` (
  `transaction_id` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `account_id` BIGINT NULL,
  `card_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `transaction_date` DATE NOT NULL,
  `transaction_time` TIME NULL,
  `transaction_type` VARCHAR(30) NOT NULL,
  `amount` BIGINT NOT NULL,
  `balance_after` BIGINT NULL,
  `merchant_name` VARCHAR(255) NULL,
  `payment_method` VARCHAR(30) NULL,
  `spending_type` VARCHAR(20) NULL,
  `summary` VARCHAR(255) NULL,
  `memo` VARCHAR(500) NULL,
  `hidden` TINYINT(1) NOT NULL DEFAULT 0,
  `source_type` VARCHAR(20) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`transaction_id`),
  KEY `idx_transaction_user_id` (`user_id`),
  KEY `idx_transaction_user_account` (`user_id`, `account_id`),
  KEY `idx_transaction_user_card` (`user_id`, `card_id`),
  KEY `idx_transaction_category_id` (`category_id`),
  KEY `idx_transaction_user_date` (`user_id`, `transaction_date`),
  CONSTRAINT `chk_transaction_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `chk_transaction_type`
    CHECK (`transaction_type` IN ('INCOME', 'EXPENSE', 'TRANSFER')),
  CONSTRAINT `chk_transaction_source_type`
    CHECK (`source_type` IN ('SSAFY', 'MANUAL', 'SEED')),
  CONSTRAINT `chk_transaction_spending_type`
    CHECK (`spending_type` IS NULL OR `spending_type` IN ('FIXED', 'VARIABLE', 'NON_SPENDING')),
  CONSTRAINT `fk_transaction_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_transaction_account`
    FOREIGN KEY (`user_id`, `account_id`)
    REFERENCES `account` (`user_id`, `account_id`),
  CONSTRAINT `fk_transaction_card`
    FOREIGN KEY (`user_id`, `card_id`)
    REFERENCES `card` (`user_id`, `card_id`),
  CONSTRAINT `fk_transaction_category`
    FOREIGN KEY (`category_id`) REFERENCES `transaction_category` (`category_id`)
);

CREATE TABLE `loan` (
  `loan_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `withdrawal_account_type` VARCHAR(30) NOT NULL,
  `financial_product_id` BIGINT NULL,
  `institution_code` VARCHAR(30) NOT NULL,
  `institution_name` VARCHAR(150) NOT NULL,
  `account_no_encrypted` VARBINARY(512) NOT NULL,
  `account_no_hash` CHAR(64) NOT NULL,
  `account_no_masked` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(150) NOT NULL,
  `loan_type` VARCHAR(30) NOT NULL,
  `repayment_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `loan_period` INT NULL,
  `start_date` DATE NULL,
  `maturity_date` DATE NULL,
  `original_principal` BIGINT NOT NULL,
  `balance` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `repayment_day` INT NULL,
  `next_repayment_date` DATE NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`loan_id`),
  UNIQUE KEY `uk_loan_user_account_hash` (`user_id`, `account_no_hash`),
  KEY `idx_loan_financial_product_id` (`financial_product_id`),
  KEY `idx_loan_user_withdrawal_account`
    (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`),
  CONSTRAINT `chk_loan_type`
    CHECK (`loan_type` IN ('CREDIT', 'MORTGAGE', 'JEONSE', 'AUTO', 'OTHER')),
  CONSTRAINT `chk_loan_repayment_type`
    CHECK (`repayment_type` IN ('EQUAL_PRINCIPAL', 'EQUAL_PAYMENT', 'BULLET')),
  CONSTRAINT `chk_loan_status`
    CHECK (`status` IN ('ACTIVE', 'OVERDUE', 'COMPLETED', 'CLOSED')),
  CONSTRAINT `chk_loan_withdrawal_account_type`
    CHECK (`withdrawal_account_type` = 'DEMAND_DEPOSIT'),
  CONSTRAINT `chk_loan_period`
    CHECK (`loan_period` IS NULL OR `loan_period` > 0),
  CONSTRAINT `chk_loan_dates`
    CHECK (`start_date` IS NULL OR `maturity_date` IS NULL OR `start_date` <= `maturity_date`),
  CONSTRAINT `chk_loan_original_principal`
    CHECK (`original_principal` > 0),
  CONSTRAINT `chk_loan_balance`
    CHECK (`balance` >= 0 AND `balance` <= `original_principal`),
  CONSTRAINT `chk_loan_interest_rate`
    CHECK (`interest_rate` >= 0),
  CONSTRAINT `chk_loan_repayment_day`
    CHECK (`repayment_day` IS NULL OR `repayment_day` BETWEEN 1 AND 31),
  CONSTRAINT `fk_loan_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_loan_financial_product`
    FOREIGN KEY (`financial_product_id`)
    REFERENCES `financial_product` (`product_id`),
  CONSTRAINT `fk_loan_withdrawal_account`
    FOREIGN KEY (`user_id`, `withdrawal_account_id`, `withdrawal_account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

CREATE TABLE `loan_repayment` (
  `loan_repayment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `loan_id` BIGINT NOT NULL,
  `repayment_date` DATE NOT NULL,
  `amount` BIGINT NOT NULL,
  `principal` BIGINT NULL,
  `interest` BIGINT NULL,
  `status` VARCHAR(30) NOT NULL,
  `failure_reason` VARCHAR(500) NULL,
  PRIMARY KEY (`loan_repayment_id`),
  KEY `idx_loan_repayment_loan_id` (`loan_id`),
  UNIQUE KEY `uk_loan_repayment_source`
    (`loan_id`, `repayment_date`, `amount`),
  CONSTRAINT `chk_loan_repayment_status`
    CHECK (`status` IN ('SCHEDULED', 'PAID', 'FAILED', 'OVERDUE')),
  CONSTRAINT `chk_loan_repayment_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `chk_loan_repayment_principal`
    CHECK (`principal` IS NULL OR `principal` >= 0),
  CONSTRAINT `chk_loan_repayment_interest`
    CHECK (`interest` IS NULL OR `interest` >= 0),
  CONSTRAINT `fk_loan_repayment_loan`
    FOREIGN KEY (`loan_id`) REFERENCES `loan` (`loan_id`)
);

CREATE TABLE `minus_account` (
  `account_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `limit_amount` BIGINT NOT NULL,
  `used_amount` BIGINT NOT NULL,
  `available_amount` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `accrued_interest` BIGINT NOT NULL,
  `next_interest_date` DATE NULL,
  `maturity_date` DATE NULL,
  `overdue_days` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_minus_account_user_account` (`user_id`, `account_id`),
  KEY `idx_minus_account_parent` (`user_id`, `account_id`, `account_type`),
  CONSTRAINT `chk_minus_account_type`
    CHECK (`account_type` = 'MINUS'),
  CONSTRAINT `chk_minus_account_limit_amount`
    CHECK (`limit_amount` > 0),
  CONSTRAINT `chk_minus_account_used_amount`
    CHECK (`used_amount` >= 0 AND `used_amount` <= `limit_amount`),
  CONSTRAINT `chk_minus_account_available_amount`
    CHECK (`available_amount` = `limit_amount` - `used_amount`),
  CONSTRAINT `chk_minus_account_interest_rate`
    CHECK (`interest_rate` >= 0),
  CONSTRAINT `chk_minus_account_accrued_interest`
    CHECK (`accrued_interest` >= 0),
  CONSTRAINT `chk_minus_account_overdue_days`
    CHECK (`overdue_days` >= 0),
  CONSTRAINT `fk_minus_account_account`
    FOREIGN KEY (`user_id`, `account_id`, `account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

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
        OR (`withdrawal_account_id` IS NOT NULL
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

CREATE TABLE `goal` (
  `goal_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(150) NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `current_amount` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`goal_id`),
  KEY `idx_goal_user_id` (`user_id`),
  UNIQUE KEY `uk_goal_user_goal_id` (`user_id`, `goal_id`),
  UNIQUE KEY `uk_goal_id_type` (`goal_id`, `type`),
  CONSTRAINT `chk_goal_type`
    CHECK (`type` IN ('SAVING', 'SPENDING')),
  CONSTRAINT `chk_goal_status`
    CHECK (`status` IN ('ACTIVE', 'ACHIEVED', 'PAUSED', 'CANCELED')),
  CONSTRAINT `chk_goal_current_amount`
    CHECK (`current_amount` >= 0),
  CONSTRAINT `fk_goal_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `saving_goal` (
  `goal_id` BIGINT NOT NULL,
  `goal_type` VARCHAR(20) NOT NULL,
  `target_amount` BIGINT NOT NULL,
  `target_date` DATE NOT NULL,
  PRIMARY KEY (`goal_id`),
  KEY `idx_saving_goal_parent` (`goal_id`, `goal_type`),
  CONSTRAINT `chk_saving_goal_type`
    CHECK (`goal_type` = 'SAVING'),
  CONSTRAINT `chk_saving_goal_target_amount`
    CHECK (`target_amount` > 0),
  CONSTRAINT `fk_saving_goal_goal`
    FOREIGN KEY (`goal_id`, `goal_type`)
    REFERENCES `goal` (`goal_id`, `type`)
);

CREATE TABLE `spending_goal` (
  `goal_id` BIGINT NOT NULL,
  `goal_type` VARCHAR(20) NOT NULL,
  `monthly_budget` BIGINT NOT NULL,
  PRIMARY KEY (`goal_id`),
  KEY `idx_spending_goal_parent` (`goal_id`, `goal_type`),
  CONSTRAINT `chk_spending_goal_type`
    CHECK (`goal_type` = 'SPENDING'),
  CONSTRAINT `chk_spending_goal_monthly_budget`
    CHECK (`monthly_budget` > 0),
  CONSTRAINT `fk_spending_goal_goal`
    FOREIGN KEY (`goal_id`, `goal_type`)
    REFERENCES `goal` (`goal_id`, `type`)
);

CREATE TABLE `goal_account` (
  `user_id` BIGINT NOT NULL,
  `goal_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `contribution_amount` BIGINT NULL,
  PRIMARY KEY (`goal_id`, `account_id`),
  UNIQUE KEY `uk_goal_account_account` (`account_id`),
  KEY `idx_goal_account_user_goal` (`user_id`, `goal_id`),
  KEY `idx_goal_account_user_account` (`user_id`, `account_id`, `account_type`),
  CONSTRAINT `chk_goal_account_type`
    CHECK (`account_type` IN ('DEMAND_DEPOSIT', 'DEPOSIT', 'SAVINGS')),
  CONSTRAINT `chk_goal_account_contribution_amount`
    CHECK (`contribution_amount` IS NULL OR `contribution_amount` >= 0),
  CONSTRAINT `fk_goal_account_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`),
  CONSTRAINT `fk_goal_account_saving_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `saving_goal` (`goal_id`),
  CONSTRAINT `fk_goal_account_account`
    FOREIGN KEY (`user_id`, `account_id`, `account_type`)
    REFERENCES `account` (`user_id`, `account_id`, `account_type`)
);

CREATE TABLE `goal_history` (
  `goal_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `goal_id` BIGINT NOT NULL,
  `recorded_date` DATE NOT NULL,
  `current_amount` BIGINT NOT NULL,
  `progress_rate` DECIMAL(10,4) NULL,
  `forecast_date` DATE NULL,
  `event` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`goal_history_id`),
  KEY `idx_goal_history_goal_id` (`goal_id`),
  CONSTRAINT `chk_goal_history_current_amount`
    CHECK (`current_amount` >= 0),
  CONSTRAINT `chk_goal_history_progress_rate`
    CHECK (`progress_rate` IS NULL OR `progress_rate` BETWEEN 0 AND 100),
  CONSTRAINT `fk_goal_history_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
);

CREATE TABLE `diagnosis` (
  `diagnosis_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `goal_id` BIGINT NULL,
  `issue_count` INT NOT NULL DEFAULT 0,
  `high_severity_count` INT NOT NULL DEFAULT 0,
  `overall_status` VARCHAR(30) NULL,
  `rule_version` VARCHAR(50) NULL,
  `input_snapshot` JSON NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`diagnosis_id`),
  UNIQUE KEY `uk_diagnosis_user_diagnosis_id` (`user_id`, `diagnosis_id`),
  KEY `idx_diagnosis_user_created_at` (`user_id`, `created_at`),
  KEY `idx_diagnosis_user_goal` (`user_id`, `goal_id`),
  CONSTRAINT `chk_diagnosis_overall_status`
    CHECK (`overall_status` IS NULL OR `overall_status` IN ('HEALTHY', 'CAUTION', 'RISK')),
  CONSTRAINT `chk_diagnosis_issue_count`
    CHECK (`issue_count` >= 0),
  CONSTRAINT `chk_diagnosis_high_severity_count`
    CHECK (`high_severity_count` >= 0 AND `high_severity_count` <= `issue_count`),
  CONSTRAINT `fk_diagnosis_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_diagnosis_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`)
);

CREATE TABLE `diagnosis_issue` (
  `issue_id` BIGINT NOT NULL AUTO_INCREMENT,
  `diagnosis_id` BIGINT NOT NULL,
  `domain` VARCHAR(50) NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `severity` VARCHAR(30) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  `current_value` JSON NULL,
  `benchmark_value` JSON NULL,
  `evidence` JSON NULL,
  `detection_source` VARCHAR(20) NOT NULL DEFAULT 'RULE',
  `model_version` VARCHAR(50) NULL,
  `cluster_context_json` JSON NULL,
  `detected_at` DATETIME NOT NULL,
  PRIMARY KEY (`issue_id`),
  KEY `idx_diagnosis_issue_filters` (`diagnosis_id`, `domain`, `type`, `severity`),
  CONSTRAINT `chk_diagnosis_issue_severity`
    CHECK (`severity` IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT `chk_diagnosis_issue_detection_source`
    CHECK (`detection_source` IN ('RULE', 'CLUSTER', 'RULE_AND_CLUSTER')),
  CONSTRAINT `fk_diagnosis_issue_diagnosis`
    FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnosis` (`diagnosis_id`)
);

CREATE TABLE `recommendation` (
  `recommendation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `diagnosis_id` BIGINT NOT NULL,
  `goal_id` BIGINT NULL,
  `domain` VARCHAR(50) NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  `priority` INT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `monthly_cost_saving` BIGINT NULL,
  `annual_cost_saving` BIGINT NULL,
  `interest_saving` BIGINT NULL,
  `risk_reduction` VARCHAR(100) NULL,
  `current_forecast_date` DATE NULL,
  `improved_forecast_date` DATE NULL,
  `advanced_days` INT NULL,
  `contribution_amount` BIGINT NULL,
  `generation_key` CHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`recommendation_id`),
  UNIQUE KEY `uk_recommendation_user_recommendation_id` (`user_id`, `recommendation_id`),
  UNIQUE KEY `uk_recommendation_user_generation_key` (`user_id`, `generation_key`),
  KEY `idx_recommendation_user_diagnosis` (`user_id`, `diagnosis_id`),
  KEY `idx_recommendation_user_goal` (`user_id`, `goal_id`),
  KEY `idx_recommendation_user_status_domain` (`user_id`, `status`, `domain`),
  CONSTRAINT `chk_recommendation_status`
    CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'DEFERRED', 'COMPLETED')),
  CONSTRAINT `chk_recommendation_priority`
    CHECK (`priority` > 0),
  CONSTRAINT `chk_recommendation_saving_amounts`
    CHECK ((`monthly_cost_saving` IS NULL OR `monthly_cost_saving` >= 0)
       AND (`annual_cost_saving` IS NULL OR `annual_cost_saving` >= 0)
       AND (`interest_saving` IS NULL OR `interest_saving` >= 0)
       AND (`contribution_amount` IS NULL OR `contribution_amount` >= 0)),
  CONSTRAINT `fk_recommendation_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_recommendation_diagnosis`
    FOREIGN KEY (`user_id`, `diagnosis_id`)
    REFERENCES `diagnosis` (`user_id`, `diagnosis_id`),
  CONSTRAINT `fk_recommendation_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`)
);

CREATE TABLE `recommendation_reason` (
  `reason_id` BIGINT NOT NULL AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `code` VARCHAR(50) NULL,
  `description` TEXT NOT NULL,
  `evidence` JSON NULL,
  PRIMARY KEY (`reason_id`),
  KEY `idx_recommendation_reason_recommendation_id` (`recommendation_id`),
  UNIQUE KEY `uk_recommendation_reason_code` (`recommendation_id`, `code`),
  CONSTRAINT `fk_recommendation_reason_recommendation`
    FOREIGN KEY (`recommendation_id`) REFERENCES `recommendation` (`recommendation_id`)
);

CREATE TABLE `recommendation_history` (
  `recommendation_history_id` BIGINT NOT NULL AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `reason` VARCHAR(500) NULL,
  `memo` VARCHAR(500) NULL,
  `changed_at` DATETIME NOT NULL,
  PRIMARY KEY (`recommendation_history_id`),
  KEY `idx_recommendation_history_recommendation_changed`
    (`recommendation_id`, `changed_at`),
  CONSTRAINT `chk_recommendation_history_status`
    CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'DEFERRED', 'COMPLETED')),
  CONSTRAINT `fk_recommendation_history_recommendation`
    FOREIGN KEY (`recommendation_id`) REFERENCES `recommendation` (`recommendation_id`)
);

CREATE TABLE `improvement_action` (
  `action_id` BIGINT NOT NULL AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `before_snapshot` JSON NULL,
  `after_snapshot` JSON NULL,
  `spending_reduction` BIGINT NULL,
  `saving_increase` BIGINT NULL,
  `debt_reduction` BIGINT NULL,
  `monthly_cost_saving` BIGINT NULL,
  `annual_cost_saving` BIGINT NULL,
  `interest_saving` BIGINT NULL,
  `goal_advanced_days` INT NULL,
  `started_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  `measured_at` DATETIME NULL,
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_improvement_action_recommendation_id` (`recommendation_id`),
  CONSTRAINT `chk_improvement_action_status`
    CHECK (`status` IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED')),
  CONSTRAINT `chk_improvement_action_amounts`
    CHECK ((`spending_reduction` IS NULL OR `spending_reduction` >= 0)
       AND (`saving_increase` IS NULL OR `saving_increase` >= 0)
       AND (`debt_reduction` IS NULL OR `debt_reduction` >= 0)
       AND (`monthly_cost_saving` IS NULL OR `monthly_cost_saving` >= 0)
       AND (`annual_cost_saving` IS NULL OR `annual_cost_saving` >= 0)
       AND (`interest_saving` IS NULL OR `interest_saving` >= 0)),
  CONSTRAINT `chk_improvement_action_dates`
    CHECK (`started_at` IS NULL OR `completed_at` IS NULL OR `started_at` <= `completed_at`),
  CONSTRAINT `fk_improvement_action_recommendation`
    FOREIGN KEY (`recommendation_id`) REFERENCES `recommendation` (`recommendation_id`)
);

CREATE TABLE `cashflow_forecast` (
  `forecast_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `base_date` DATE NOT NULL,
  `horizon_days` INT NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `current_balance` BIGINT NULL,
  `available_today` BIGINT NULL,
  `minimum_balance` BIGINT NULL,
  `risk_date` DATE NULL,
  `calculation_version` VARCHAR(50) NULL,
  `request_hash` CHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`forecast_id`),
  KEY `idx_cashflow_forecast_user_base_date` (`user_id`, `base_date`, `created_at`),
  KEY `idx_cashflow_forecast_user_request_hash` (`user_id`, `request_hash`),
  CONSTRAINT `chk_cashflow_forecast_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `chk_cashflow_forecast_horizon_days`
    CHECK (`horizon_days` > 0),
  CONSTRAINT `fk_cashflow_forecast_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `cashflow_daily_balance` (
  `daily_balance_id` BIGINT NOT NULL AUTO_INCREMENT,
  `forecast_id` BIGINT NOT NULL,
  `balance_date` DATE NOT NULL,
  `expected_balance` BIGINT NOT NULL,
  PRIMARY KEY (`daily_balance_id`),
  UNIQUE KEY `uk_cashflow_daily_balance_forecast_date` (`forecast_id`, `balance_date`),
  CONSTRAINT `fk_cashflow_daily_balance_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);

CREATE TABLE `cashflow_event` (
  `event_id` BIGINT NOT NULL AUTO_INCREMENT,
  `forecast_id` BIGINT NOT NULL,
  `event_date` DATE NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `direction` VARCHAR(10) NOT NULL,
  `amount` BIGINT NOT NULL,
  `source_type` VARCHAR(30) NULL,
  `source_id` VARCHAR(100) NULL,
  `dedupe_key` CHAR(64) NOT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_cashflow_event_forecast_dedupe` (`forecast_id`, `dedupe_key`),
  KEY `idx_cashflow_event_forecast_date_type` (`forecast_id`, `event_date`, `type`),
  CONSTRAINT `chk_cashflow_event_direction`
    CHECK (`direction` IN ('INFLOW', 'OUTFLOW')),
  CONSTRAINT `chk_cashflow_event_amount`
    CHECK (`amount` > 0),
  CONSTRAINT `fk_cashflow_event_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);

CREATE TABLE `cashflow_risk` (
  `risk_id` BIGINT NOT NULL AUTO_INCREMENT,
  `forecast_id` BIGINT NOT NULL,
  `risk_date` DATE NULL,
  `type` VARCHAR(50) NOT NULL,
  `severity` VARCHAR(30) NOT NULL,
  `message` VARCHAR(500) NULL,
  `expected_balance` BIGINT NULL,
  PRIMARY KEY (`risk_id`),
  KEY `idx_cashflow_risk_forecast_id` (`forecast_id`),
  CONSTRAINT `chk_cashflow_risk_severity`
    CHECK (`severity` IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT `fk_cashflow_risk_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);

CREATE TABLE `simulation` (
  `simulation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NULL,
  `summary` VARCHAR(500) NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
  `request_snapshot` JSON NULL,
  `before_snapshot` JSON NOT NULL,
  `after_snapshot` JSON NOT NULL,
  `comparison` JSON NULL,
  `cashflow_impact` JSON NULL,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`simulation_id`),
  UNIQUE KEY `uk_simulation_user_simulation_id` (`user_id`, `simulation_id`),
  KEY `idx_simulation_user_type_created` (`user_id`, `type`, `created_at`),
  CONSTRAINT `chk_simulation_type`
    CHECK (`type` IN ('SPENDING', 'SAVING', 'SAVING_PRODUCT', 'LOAN_PREPAYMENT',
                      'LOAN_REFINANCE', 'LOAN_NEW', 'INSTALLMENT', 'REVOLVING',
                      'CASH_ADVANCE', 'COMBINED')),
  CONSTRAINT `chk_simulation_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  CONSTRAINT `fk_simulation_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `simulation_scenario` (
  `scenario_id` BIGINT NOT NULL AUTO_INCREMENT,
  `simulation_id` BIGINT NOT NULL,
  `sequence_no` INT NOT NULL,
  `scenario_type` VARCHAR(50) NOT NULL,
  `parameters` JSON NOT NULL,
  PRIMARY KEY (`scenario_id`),
  UNIQUE KEY `uk_simulation_scenario_sequence` (`simulation_id`, `sequence_no`),
  CONSTRAINT `chk_simulation_scenario_sequence_no`
    CHECK (`sequence_no` > 0),
  CONSTRAINT `fk_simulation_scenario_simulation`
    FOREIGN KEY (`simulation_id`) REFERENCES `simulation` (`simulation_id`)
);

CREATE TABLE `simulation_goal_impact` (
  `simulation_goal_impact_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `simulation_id` BIGINT NOT NULL,
  `goal_id` BIGINT NOT NULL,
  `before_forecast_date` DATE NULL,
  `after_forecast_date` DATE NULL,
  `difference_days` INT NULL,
  PRIMARY KEY (`simulation_goal_impact_id`),
  UNIQUE KEY `uk_simulation_goal_impact_simulation_goal`
    (`simulation_id`, `goal_id`),
  KEY `idx_simulation_goal_impact_user_simulation` (`user_id`, `simulation_id`),
  KEY `idx_simulation_goal_impact_user_goal` (`user_id`, `goal_id`),
  CONSTRAINT `fk_simulation_goal_impact_simulation`
    FOREIGN KEY (`user_id`, `simulation_id`)
    REFERENCES `simulation` (`user_id`, `simulation_id`),
  CONSTRAINT `fk_simulation_goal_impact_goal`
    FOREIGN KEY (`user_id`, `goal_id`)
    REFERENCES `goal` (`user_id`, `goal_id`)
);

CREATE TABLE `product_sync_history` (
  `sync_id` BIGINT NOT NULL AUTO_INCREMENT,
  `sync_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `fetched_count` INT NULL,
  `inserted_count` INT NULL,
  `updated_count` INT NULL,
  `failed_count` INT NULL,
  `error_message` VARCHAR(1000) NULL,
  `started_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sync_id`),
  CONSTRAINT `chk_product_sync_history_status`
    CHECK (`status` IN ('PENDING', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
  CONSTRAINT `chk_product_sync_history_counts`
    CHECK ((`fetched_count` IS NULL OR `fetched_count` >= 0)
       AND (`inserted_count` IS NULL OR `inserted_count` >= 0)
       AND (`updated_count` IS NULL OR `updated_count` >= 0)
       AND (`failed_count` IS NULL OR `failed_count` >= 0))
);

CREATE TABLE `report` (
  `report_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `period` VARCHAR(20) NOT NULL,
  `period_start` DATE NOT NULL,
  `period_end` DATE NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_id_type` (`report_id`, `type`),
  UNIQUE KEY `uk_report_user_type_period` (`user_id`, `type`, `period`),
  KEY `idx_report_user_period_range` (`user_id`, `period_start`, `period_end`),
  CONSTRAINT `chk_report_type`
    CHECK (`type` IN ('DAILY', 'MONTHLY')),
  CONSTRAINT `chk_report_period_dates`
    CHECK (`period_start` <= `period_end`),
  CONSTRAINT `fk_report_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `daily_report` (
  `report_id` BIGINT NOT NULL,
  `report_type` VARCHAR(20) NOT NULL,
  `report_date` DATE NOT NULL,
  `summary` TEXT NULL,
  `available_today` BIGINT NOT NULL,
  `upcoming_events` JSON NULL,
  `risks` JSON NULL,
  `recommendations` JSON NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_daily_report_parent` (`report_id`, `report_type`),
  CONSTRAINT `chk_daily_report_type`
    CHECK (`report_type` = 'DAILY'),
  CONSTRAINT `fk_daily_report_report`
    FOREIGN KEY (`report_id`, `report_type`)
    REFERENCES `report` (`report_id`, `type`)
);

CREATE TABLE `monthly_report` (
  `report_id` BIGINT NOT NULL,
  `report_type` VARCHAR(20) NOT NULL,
  `year_month` CHAR(7) NOT NULL,
  `financial_summary` JSON NULL,
  `spending_summary` JSON NULL,
  `goal_summary` JSON NULL,
  `improvement_summary` JSON NULL,
  `upcoming_summary` JSON NULL,
  `llm_comment` TEXT NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_monthly_report_parent` (`report_id`, `report_type`),
  CONSTRAINT `chk_monthly_report_type`
    CHECK (`report_type` = 'MONTHLY'),
  CONSTRAINT `fk_monthly_report_report`
    FOREIGN KEY (`report_id`, `report_type`)
    REFERENCES `report` (`report_id`, `type`)
);

CREATE TABLE `notification` (
  `notification_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `message` TEXT NOT NULL,
  `source_type` VARCHAR(50) NULL,
  `source_id` VARCHAR(100) NULL,
  `dedupe_key` CHAR(64) NULL,
  `is_read` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `read_at` DATETIME NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notification_user_dedupe` (`user_id`, `dedupe_key`),
  KEY `idx_notification_user_filters` (`user_id`, `is_read`, `type`, `created_at`),
  CONSTRAINT `fk_notification_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
