package com.ssafy.dodam.domain.account.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountTransactionReader implements AccountTransactionReader {

    private static final String SELECT_SQL = """
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
                category.name AS category_name,
                entry.hidden
            FROM `transaction` entry
            LEFT JOIN transaction_category category
                ON category.category_id = entry.category_id
            """;

    // 소유권은 user_id 조건으로 SQL 단계에서 강제한다.
    private static final String LIST_WHERE = """
            WHERE entry.user_id = :userId
              AND entry.account_id = :accountId
              AND entry.hidden = FALSE
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String ORDER_AND_PAGE = """
            ORDER BY entry.transaction_date DESC, entry.transaction_time DESC,
                     entry.transaction_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM `transaction` entry
            """ + LIST_WHERE;

    private static final String ONE_SQL = SELECT_SQL + """
            WHERE entry.user_id = :userId
              AND entry.account_id = :accountId
              AND entry.transaction_id = :transactionId
            """;

    private static final RowMapper<AccountTransactionRow> ROW_MAPPER = JdbcAccountTransactionReader::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAccountTransactionReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<AccountTransactionRow> read(Long userId, Long accountId, QueryPeriod period, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("accountId", accountId)
                .addValue("startDate", period.startDate())
                .addValue("endDate", period.endDate());

        long total = Objects.requireNonNullElse(jdbcTemplate.queryForObject(COUNT_SQL, parameters, Long.class), 0L);
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        parameters.addValue("limit", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());
        List<AccountTransactionRow> rows = jdbcTemplate.query(SELECT_SQL + LIST_WHERE + ORDER_AND_PAGE, parameters, ROW_MAPPER);
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public Optional<AccountTransactionRow> findOne(Long userId, Long accountId, String transactionId) {
        List<AccountTransactionRow> rows = jdbcTemplate.query(
                ONE_SQL,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("accountId", accountId)
                        .addValue("transactionId", transactionId),
                ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    private static AccountTransactionRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AccountTransactionRow(
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
                resultSet.getString("category_name"),
                resultSet.getBoolean("hidden")
        );
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
