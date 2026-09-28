package com.ssafy.dodam.domain.cashflow.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CashflowSourceReaderTest {
    private JdbcTemplate jdbc;
    private CashflowSourceReader reader;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        reader = new CashflowSourceReader(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void missingSubscriptionTableIsExplicit() {
        assertThat(reader.subscriptions(1L).available()).isFalse();
    }

    @Test
    void subscriptionsAreOwnerScopedAndOnlyActiveRowsAreRead() {
        jdbc.execute("""
                CREATE TABLE subscription (subscription_id VARCHAR(100), user_id BIGINT, card_id BIGINT,
                payment_amount BIGINT, billing_cycle VARCHAR(30), payment_day INT,
                next_payment_date DATE, end_date DATE, status VARCHAR(30))
                """);
        jdbc.update("INSERT INTO subscription VALUES ('own', 1, NULL, 100, 'MONTHLY', 1, '2026-01-01', NULL, 'ACTIVE')");
        jdbc.update("INSERT INTO subscription VALUES ('other', 2, NULL, 100, 'MONTHLY', 1, '2026-01-01', NULL, 'ACTIVE')");
        jdbc.update("INSERT INTO subscription VALUES ('paused', 1, NULL, 100, 'MONTHLY', 1, '2026-01-01', NULL, 'PAUSED')");
        assertThat(reader.subscriptions(1L).rows()).extracting(CashflowSourceReader.Subscription::id)
                .containsExactly("own");
        assertThat(reader.subscriptions(3L).rows()).isEmpty();
    }

    @Test
    void repaymentsAreOwnerScopedAndKeepAllStatusesForSuppression() {
        jdbc.execute("CREATE TABLE loan (loan_id BIGINT, user_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE loan_repayment (loan_repayment_id BIGINT, loan_id BIGINT,
                repayment_date DATE, amount BIGINT, status VARCHAR(30))
                """);
        jdbc.update("INSERT INTO loan VALUES (10, 1), (20, 2)");
        jdbc.update("INSERT INTO loan_repayment VALUES (1, 10, '2026-01-01', 100, 'PAID'), (2, 20, '2026-01-01', 200, 'SCHEDULED')");
        assertThat(reader.repayments(1L)).extracting(CashflowSourceReader.Repayment::status).containsExactly("PAID");
        assertThat(reader.repayments(3L)).isEmpty();
    }
}
