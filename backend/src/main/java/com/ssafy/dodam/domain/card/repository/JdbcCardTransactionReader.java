package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardTransactionReader implements CardTransactionReader {

    private static final String WHERE_CLAUSE = """
            WHERE spend.user_id = :userId
              AND spend.card_id = :cardId
              AND spend.hidden = FALSE
              AND spend.transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String CONTENT_SQL = """
            SELECT
                spend.transaction_id,
                spend.transaction_date,
                spend.transaction_time,
                spend.transaction_type,
                spend.amount,
                spend.merchant_name,
                spend.payment_method,
                category.code AS category_code,
                category.name AS category_name,
                spend.summary,
                spend.memo
            FROM `transaction` spend
            LEFT JOIN transaction_category category
                ON category.category_id = spend.category_id
            """
            + WHERE_CLAUSE
            + """
            ORDER BY spend.transaction_date DESC, spend.transaction_time DESC,
                     spend.transaction_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM `transaction` spend
            """
            + WHERE_CLAUSE;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCardTransactionReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<CardTransactionRow> read(
            Long userId,
            Long cardId,
            QueryPeriod period,
            Pageable pageable
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        parameters.put("cardId", cardId);
        parameters.put("startDate", period.startDate());
        parameters.put("endDate", period.endDate());

        long total = Objects.requireNonNullElse(
                jdbcTemplate.queryForObject(COUNT_SQL, parameters, Long.class),
                0L
        );
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        parameters.put("limit", pageable.getPageSize());
        parameters.put("offset", pageable.getOffset());
        List<CardTransactionRow> rows = jdbcTemplate.query(
                CONTENT_SQL,
                parameters,
                (resultSet, rowNumber) -> new CardTransactionRow(
                        resultSet.getString("transaction_id"),
                        resultSet.getObject("transaction_date", LocalDate.class),
                        toLocalTime(resultSet.getObject("transaction_time")),
                        resultSet.getString("transaction_type"),
                        resultSet.getLong("amount"),
                        resultSet.getString("merchant_name"),
                        resultSet.getString("payment_method"),
                        resultSet.getString("category_code"),
                        resultSet.getString("category_name"),
                        resultSet.getString("summary"),
                        resultSet.getString("memo")
                )
        );
        return new PageImpl<>(rows, pageable, total);
    }

    private static LocalTime toLocalTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalTime localTime) {
            return localTime;
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        return LocalTime.parse(value.toString());
    }
}
