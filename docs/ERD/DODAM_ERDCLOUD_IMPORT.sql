-- DODAM ERDCloud Import DDL
-- Dialect: MySQL
-- Basis: latest agreed entity list + relationship/cardinality table
-- Import in ERDCloud via Import > DDL / SQL.

CREATE TABLE `user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_email` (`email`)
);

CREATE TABLE `user_setting` (
  `user_id` BIGINT NOT NULL,
  `notification_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `daily_briefing_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `monthly_report_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`user_id`),
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
  CONSTRAINT `fk_financial_profile_user`
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
  `analyzed_at` DATETIME NOT NULL,
  PRIMARY KEY (`analysis_id`),
  KEY `idx_pattern_analysis_user_id` (`user_id`),
  CONSTRAINT `fk_pattern_analysis_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `finance_connection` (
  `connection_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `member_email` VARCHAR(255) NULL,
  `status` VARCHAR(30) NOT NULL,
  `connected_at` DATETIME NULL,
  `disconnected_at` DATETIME NULL,
  `last_synced_at` DATETIME NULL,
  PRIMARY KEY (`connection_id`),
  UNIQUE KEY `uk_finance_connection_user_id` (`user_id`),
  CONSTRAINT `fk_finance_connection_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `finance_sync` (
  `sync_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `scope` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `progress` INT NULL,
  `started_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`sync_id`),
  KEY `idx_finance_sync_user_id` (`user_id`),
  CONSTRAINT `fk_finance_sync_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `account` (
  `account_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `bank_code` VARCHAR(20) NOT NULL,
  `bank_name` VARCHAR(100) NOT NULL,
  `account_no` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(100) NOT NULL,
  `account_type` VARCHAR(30) NOT NULL,
  `balance` BIGINT NOT NULL,
  `last_transaction_date` DATE NULL,
  `currency` VARCHAR(10) NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_account_user_account_no` (`user_id`, `account_no`),
  CONSTRAINT `fk_account_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `transaction_category` (
  `category_id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`category_id`)
);

CREATE TABLE `card` (
  `card_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `card_no` VARCHAR(100) NOT NULL,
  `issuer_name` VARCHAR(100) NOT NULL,
  `card_name` VARCHAR(150) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `baseline_performance` BIGINT NULL,
  `max_benefit_limit` BIGINT NULL,
  `annual_fee` BIGINT NULL,
  `expiry_month` CHAR(7) NULL,
  `withdrawal_day` INT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`card_id`),
  UNIQUE KEY `uk_card_user_card_no` (`user_id`, `card_no`),
  KEY `idx_card_withdrawal_account_id` (`withdrawal_account_id`),
  CONSTRAINT `fk_card_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_card_withdrawal_account`
    FOREIGN KEY (`withdrawal_account_id`) REFERENCES `account` (`account_id`)
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
  KEY `idx_subscription_card_id` (`card_id`),
  CONSTRAINT `fk_subscription_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_subscription_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
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
  `status` VARCHAR(30) NOT NULL,
  PRIMARY KEY (`installment_id`),
  KEY `idx_installment_card_id` (`card_id`),
  CONSTRAINT `fk_installment_card`
    FOREIGN KEY (`card_id`) REFERENCES `card` (`card_id`)
);

CREATE TABLE `transaction` (
  `transaction_id` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `account_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `transaction_date` DATE NOT NULL,
  `transaction_time` TIME NULL,
  `transaction_type` VARCHAR(30) NOT NULL,
  `amount` BIGINT NOT NULL,
  `balance_after` BIGINT NULL,
  `merchant_name` VARCHAR(255) NULL,
  `payment_method` VARCHAR(30) NULL,
  `summary` VARCHAR(255) NULL,
  `memo` VARCHAR(500) NULL,
  `hidden` TINYINT(1) NOT NULL DEFAULT 0,
  `source_type` VARCHAR(20) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`transaction_id`),
  KEY `idx_transaction_user_id` (`user_id`),
  KEY `idx_transaction_account_id` (`account_id`),
  KEY `idx_transaction_category_id` (`category_id`),
  CONSTRAINT `fk_transaction_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_transaction_account`
    FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`),
  CONSTRAINT `fk_transaction_category`
    FOREIGN KEY (`category_id`) REFERENCES `transaction_category` (`category_id`)
);

CREATE TABLE `loan` (
  `loan_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `account_no` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(150) NOT NULL,
  `loan_type` VARCHAR(30) NOT NULL,
  `repayment_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `loan_period` INT NULL,
  `start_date` DATE NULL,
  `maturity_date` DATE NULL,
  `balance` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  PRIMARY KEY (`loan_id`),
  UNIQUE KEY `uk_loan_user_account_no` (`user_id`, `account_no`),
  KEY `idx_loan_withdrawal_account_id` (`withdrawal_account_id`),
  CONSTRAINT `fk_loan_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_loan_withdrawal_account`
    FOREIGN KEY (`withdrawal_account_id`) REFERENCES `account` (`account_id`)
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
  CONSTRAINT `fk_loan_repayment_loan`
    FOREIGN KEY (`loan_id`) REFERENCES `loan` (`loan_id`)
);

CREATE TABLE `minus_account` (
  `minus_account_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `account_no` VARCHAR(100) NOT NULL,
  `limit_amount` BIGINT NOT NULL,
  `used_amount` BIGINT NOT NULL,
  `available_amount` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `accrued_interest` BIGINT NOT NULL,
  `next_interest_date` DATE NULL,
  `maturity_date` DATE NULL,
  `status` VARCHAR(30) NOT NULL,
  `overdue_days` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`minus_account_id`),
  UNIQUE KEY `uk_minus_account_user_account_no` (`user_id`, `account_no`),
  CONSTRAINT `fk_minus_account_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `deposit_saving` (
  `saving_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `withdrawal_account_id` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `account_no` VARCHAR(100) NOT NULL,
  `account_name` VARCHAR(150) NOT NULL,
  `balance` BIGINT NOT NULL,
  `interest_rate` DECIMAL(10,4) NOT NULL,
  `opening_date` DATE NOT NULL,
  `expiry_date` DATE NULL,
  `status` VARCHAR(30) NOT NULL,
  PRIMARY KEY (`saving_id`),
  UNIQUE KEY `uk_deposit_saving_user_account_no` (`user_id`, `account_no`),
  KEY `idx_deposit_saving_withdrawal_account_id` (`withdrawal_account_id`),
  CONSTRAINT `fk_deposit_saving_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_deposit_saving_withdrawal_account`
    FOREIGN KEY (`withdrawal_account_id`) REFERENCES `account` (`account_id`)
);

CREATE TABLE `savings_payment` (
  `savings_payment_id` BIGINT NOT NULL AUTO_INCREMENT,
  `deposit_saving_id` BIGINT NOT NULL,
  `payment_date` DATE NOT NULL,
  `amount` BIGINT NOT NULL,
  `installment_number` INT NULL,
  `status` VARCHAR(30) NOT NULL,
  PRIMARY KEY (`savings_payment_id`),
  KEY `idx_savings_payment_deposit_saving_id` (`deposit_saving_id`),
  CONSTRAINT `fk_savings_payment_deposit_saving`
    FOREIGN KEY (`deposit_saving_id`) REFERENCES `deposit_saving` (`saving_id`)
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
  PRIMARY KEY (`goal_id`),
  KEY `idx_goal_user_id` (`user_id`),
  CONSTRAINT `fk_goal_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `saving_goal` (
  `goal_id` BIGINT NOT NULL,
  `target_amount` BIGINT NOT NULL,
  `target_date` DATE NOT NULL,
  PRIMARY KEY (`goal_id`),
  CONSTRAINT `fk_saving_goal_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
);

CREATE TABLE `spending_goal` (
  `goal_id` BIGINT NOT NULL,
  `monthly_budget` BIGINT NOT NULL,
  PRIMARY KEY (`goal_id`),
  CONSTRAINT `fk_spending_goal_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
);

CREATE TABLE `goal_account` (
  `goal_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `contribution_amount` BIGINT NULL,
  PRIMARY KEY (`goal_id`, `account_id`),
  KEY `idx_goal_account_account_id` (`account_id`),
  CONSTRAINT `fk_goal_account_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`),
  CONSTRAINT `fk_goal_account_account`
    FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`)
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
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`diagnosis_id`),
  KEY `idx_diagnosis_user_id` (`user_id`),
  KEY `idx_diagnosis_goal_id` (`goal_id`),
  CONSTRAINT `fk_diagnosis_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `fk_diagnosis_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
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
  `detected_at` DATETIME NOT NULL,
  PRIMARY KEY (`issue_id`),
  KEY `idx_diagnosis_issue_diagnosis_id` (`diagnosis_id`),
  CONSTRAINT `fk_diagnosis_issue_diagnosis`
    FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnosis` (`diagnosis_id`)
);

CREATE TABLE `recommendation` (
  `recommendation_id` BIGINT NOT NULL AUTO_INCREMENT,
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
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`recommendation_id`),
  KEY `idx_recommendation_diagnosis_id` (`diagnosis_id`),
  KEY `idx_recommendation_goal_id` (`goal_id`),
  CONSTRAINT `fk_recommendation_diagnosis`
    FOREIGN KEY (`diagnosis_id`) REFERENCES `diagnosis` (`diagnosis_id`),
  CONSTRAINT `fk_recommendation_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
);

CREATE TABLE `recommendation_reason` (
  `reason_id` BIGINT NOT NULL AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `code` VARCHAR(50) NULL,
  `description` TEXT NOT NULL,
  `evidence` JSON NULL,
  PRIMARY KEY (`reason_id`),
  KEY `idx_recommendation_reason_recommendation_id` (`recommendation_id`),
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
  KEY `idx_recommendation_history_recommendation_id` (`recommendation_id`),
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
  `started_at` DATETIME NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_improvement_action_recommendation_id` (`recommendation_id`),
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
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`forecast_id`),
  KEY `idx_cashflow_forecast_user_id` (`user_id`),
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
  `amount` BIGINT NOT NULL,
  `source_type` VARCHAR(30) NULL,
  `source_id` VARCHAR(100) NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_cashflow_event_forecast_id` (`forecast_id`),
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
  CONSTRAINT `fk_cashflow_risk_forecast`
    FOREIGN KEY (`forecast_id`) REFERENCES `cashflow_forecast` (`forecast_id`)
);

CREATE TABLE `simulation` (
  `simulation_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NULL,
  `summary` VARCHAR(500) NULL,
  `request_snapshot` JSON NULL,
  `before_snapshot` JSON NOT NULL,
  `after_snapshot` JSON NOT NULL,
  `comparison` JSON NULL,
  `cashflow_impact` JSON NULL,
  `created_at` DATETIME NOT NULL,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`simulation_id`),
  KEY `idx_simulation_user_id` (`user_id`),
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
  CONSTRAINT `fk_simulation_scenario_simulation`
    FOREIGN KEY (`simulation_id`) REFERENCES `simulation` (`simulation_id`)
);

CREATE TABLE `simulation_goal_impact` (
  `simulation_id` BIGINT NOT NULL,
  `goal_id` BIGINT NULL,
  `before_forecast_date` DATE NULL,
  `after_forecast_date` DATE NULL,
  `difference_days` INT NOT NULL,
  PRIMARY KEY (`simulation_id`),
  KEY `idx_simulation_goal_impact_goal_id` (`goal_id`),
  CONSTRAINT `fk_simulation_goal_impact_simulation`
    FOREIGN KEY (`simulation_id`) REFERENCES `simulation` (`simulation_id`),
  CONSTRAINT `fk_simulation_goal_impact_goal`
    FOREIGN KEY (`goal_id`) REFERENCES `goal` (`goal_id`)
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
  KEY `idx_financial_product_company_id` (`company_id`),
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
  CONSTRAINT `fk_financial_product_option_product`
    FOREIGN KEY (`product_id`) REFERENCES `financial_product` (`product_id`)
);

CREATE TABLE `product_sync_history` (
  `sync_id` BIGINT NOT NULL AUTO_INCREMENT,
  `sync_type` VARCHAR(30) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `fetched_count` INT NULL,
  `inserted_count` INT NULL,
  `updated_count` INT NULL,
  `failed_count` INT NULL,
  `started_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  PRIMARY KEY (`sync_id`)
);

CREATE TABLE `report` (
  `report_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `period` VARCHAR(20) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_report_user_id` (`user_id`),
  CONSTRAINT `fk_report_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);

CREATE TABLE `daily_report` (
  `report_id` BIGINT NOT NULL,
  `report_date` DATE NOT NULL,
  `summary` TEXT NULL,
  `available_today` BIGINT NOT NULL,
  `upcoming_events` JSON NULL,
  `risks` JSON NULL,
  `recommendations` JSON NULL,
  PRIMARY KEY (`report_id`),
  CONSTRAINT `fk_daily_report_report`
    FOREIGN KEY (`report_id`) REFERENCES `report` (`report_id`)
);

CREATE TABLE `monthly_report` (
  `report_id` BIGINT NOT NULL,
  `year_month` CHAR(7) NOT NULL,
  `financial_summary` JSON NULL,
  `spending_summary` JSON NULL,
  `goal_summary` JSON NULL,
  `improvement_summary` JSON NULL,
  `upcoming_summary` JSON NULL,
  `llm_comment` TEXT NULL,
  PRIMARY KEY (`report_id`),
  CONSTRAINT `fk_monthly_report_report`
    FOREIGN KEY (`report_id`) REFERENCES `report` (`report_id`)
);

CREATE TABLE `notification` (
  `notification_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `message` TEXT NOT NULL,
  `is_read` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `read_at` DATETIME NULL,
  PRIMARY KEY (`notification_id`),
  KEY `idx_notification_user_id` (`user_id`),
  CONSTRAINT `fk_notification_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
);
