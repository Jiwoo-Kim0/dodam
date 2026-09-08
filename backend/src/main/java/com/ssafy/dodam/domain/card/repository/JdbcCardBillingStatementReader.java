package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.domain.card.entity.CardBillingStatementStatus;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardBillingStatementReader implements CardBillingStatementReader {

    private static final String SELECT_COLUMNS = """
            SELECT billing_month, billing_date, total_balance, status, payment_date
            FROM card_billing_statement
            """;

    private static final String NEXT_SCHEDULED_SQL = SELECT_COLUMNS + """
            WHERE card_id = :cardId
              AND status = 'SCHEDULED'
            ORDER BY billing_date ASC, billing_month ASC
            LIMIT 1
            """;

    private static final String PERIOD_SQL = SELECT_COLUMNS + """
            WHERE card_id = :cardId
              AND billing_month BETWEEN :startMonth AND :endMonth
            ORDER BY billing_month DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCardBillingStatementReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CardBillingStatementRow> findNextScheduled(Long cardId) {
        return jdbcTemplate.query(
                NEXT_SCHEDULED_SQL,
                Map.of("cardId", cardId),
                (resultSet, rowNumber) -> mapRow(resultSet)
        ).stream().findFirst();
    }

    @Override
    public List<CardBillingStatementRow> read(Long cardId, YearMonthPeriod period) {
        return jdbcTemplate.query(
                PERIOD_SQL,
                Map.of(
                        "cardId", cardId,
                        "startMonth", period.startMonth().toString(),
                        "endMonth", period.endMonth().toString()
                ),
                (resultSet, rowNumber) -> mapRow(resultSet)
        );
    }

    private static CardBillingStatementRow mapRow(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new CardBillingStatementRow(
                YearMonth.parse(resultSet.getString("billing_month")),
                resultSet.getObject("billing_date", LocalDate.class),
                resultSet.getLong("total_balance"),
                CardBillingStatementStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("payment_date", LocalDate.class)
        );
    }
}
