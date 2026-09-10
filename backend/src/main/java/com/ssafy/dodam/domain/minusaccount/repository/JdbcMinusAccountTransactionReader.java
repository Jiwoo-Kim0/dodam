package com.ssafy.dodam.domain.minusaccount.repository;

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
public class JdbcMinusAccountTransactionReader implements MinusAccountTransactionReader {

    // 소유권은 user_id 조건으로 SQL 단계에서 강제한다. 숨김 처리한 거래는 목록에서 제외한다.
    private static final String WHERE_CLAUSE = """
            WHERE entry.user_id = :userId
              AND entry.account_id = :accountId
              AND entry.hidden = FALSE
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String CONTENT_SQL = """
            SELECT
                entry.transaction_id,
                entry.transaction_date,
                entry.transaction_time,
                entry.transaction_type,
                entry.amount,
                entry.balance_after,
                entry.merchant_name,
                entry.summary,
                entry.memo,
                category.code AS category_code,
                category.name AS category_name
            FROM `transaction` entry
            LEFT JOIN transaction_category category
                ON category.category_id = entry.category_id
            """
            + WHERE_CLAUSE
            + """
            ORDER BY entry.transaction_date DESC, entry.transaction_time DESC,
                     entry.transaction_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM `transaction` entry
            """
            + WHERE_CLAUSE;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMinusAccountTransactionReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<MinusAccountTransactionRow> read(
            Long userId,
            Long accountId,
            QueryPeriod period,
            Pageable pageable
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        parameters.put("accountId", accountId);
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
        List<MinusAccountTransactionRow> rows = jdbcTemplate.query(
                CONTENT_SQL,
                parameters,
                (resultSet, rowNumber) -> new MinusAccountTransactionRow(
                        resultSet.getString("transaction_id"),
                        resultSet.getObject("transaction_date", LocalDate.class),
                        toLocalTime(resultSet.getObject("transaction_time")),
                        resultSet.getString("transaction_type"),
                        resultSet.getLong("amount"),
                        resultSet.getObject("balance_after", Long.class),
                        resultSet.getString("merchant_name"),
                        resultSet.getString("summary"),
                        resultSet.getString("memo"),
                        resultSet.getString("category_code"),
                        resultSet.getString("category_name")
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
