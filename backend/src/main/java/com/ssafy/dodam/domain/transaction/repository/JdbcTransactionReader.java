package com.ssafy.dodam.domain.transaction.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/**
 * 카드(8)·마이너스통장(10-1) 거래내역 reader와 같은 방식으로 `transaction`을 직접 읽는다.
 * 선택 조건은 파라미터가 있을 때만 WHERE에 붙여 MySQL·H2 양쪽에서 NULL 파라미터 타입 문제를 피한다.
 */
@Repository
public class JdbcTransactionReader implements TransactionReader {

    private static final String SELECT_SQL = """
            SELECT
                entry.transaction_id,
                entry.account_id,
                entry.card_id,
                entry.category_id,
                category.code AS category_code,
                category.name AS category_name,
                entry.transaction_date,
                entry.transaction_time,
                entry.transaction_type,
                entry.amount,
                entry.balance_after,
                entry.merchant_name,
                entry.payment_method,
                entry.spending_type,
                entry.summary,
                entry.memo,
                entry.hidden,
                entry.source_type,
                entry.created_at,
                entry.updated_at
            FROM `transaction` entry
            LEFT JOIN transaction_category category
                ON category.category_id = entry.category_id
            """;

    private static final String ORDER_AND_PAGE_SQL = """
            ORDER BY entry.transaction_date DESC, entry.transaction_time DESC,
                     entry.transaction_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final RowMapper<TransactionRow> ROW_MAPPER = JdbcTransactionReader::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTransactionReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<TransactionRow> read(Long userId, TransactionFilter filter, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startDate", filter.period().startDate())
                .addValue("endDate", filter.period().endDate());
        StringBuilder where = new StringBuilder("""
                WHERE entry.user_id = :userId
                  AND entry.transaction_date BETWEEN :startDate AND :endDate
                """);
        if (filter.hidden() != null) {
            where.append("  AND entry.hidden = :hidden\n");
            parameters.addValue("hidden", filter.hidden());
        }
        if (filter.categoryCode() != null) {
            where.append("  AND category.code = :categoryCode\n");
            parameters.addValue("categoryCode", filter.categoryCode());
        }
        if (filter.paymentMethod() != null) {
            where.append("  AND entry.payment_method = :paymentMethod\n");
            parameters.addValue("paymentMethod", filter.paymentMethod());
        }

        parameters.addValue("limit", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());
        List<TransactionRow> rows = jdbcTemplate.query(
                SELECT_SQL + where + ORDER_AND_PAGE_SQL,
                parameters,
                ROW_MAPPER
        );
        // 마지막 페이지가 꽉 차지 않았다면 조회 결과만으로 정확한 총건수를 알 수 있다.
        // 첫 페이지의 빈 결과도 COUNT가 필요 없다. 빈 후속 페이지와 꽉 찬 페이지에서만 센다.
        if (rows.size() < pageable.getPageSize() && (!rows.isEmpty() || pageable.getOffset() == 0)) {
            return new PageImpl<>(rows, pageable, pageable.getOffset() + rows.size());
        }
        String countSql = "SELECT COUNT(*) FROM `transaction` entry\n"
                + (filter.categoryCode() == null ? "" : """
                LEFT JOIN transaction_category category
                    ON category.category_id = entry.category_id
                """) + where;
        long total = Objects.requireNonNullElse(
                jdbcTemplate.queryForObject(countSql, parameters, Long.class),
                0L
        );
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public Optional<TransactionRow> findOne(Long userId, String transactionId) {
        List<TransactionRow> rows = jdbcTemplate.query(
                SELECT_SQL + """
                        WHERE entry.user_id = :userId
                          AND entry.transaction_id = :transactionId
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("transactionId", transactionId),
                ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    private static TransactionRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TransactionRow(
                resultSet.getString("transaction_id"),
                resultSet.getObject("account_id", Long.class),
                resultSet.getObject("card_id", Long.class),
                resultSet.getObject("category_id", Long.class),
                resultSet.getString("category_code"),
                resultSet.getString("category_name"),
                resultSet.getObject("transaction_date", LocalDate.class),
                toLocalTime(resultSet.getObject("transaction_time")),
                resultSet.getString("transaction_type"),
                resultSet.getLong("amount"),
                resultSet.getObject("balance_after", Long.class),
                resultSet.getString("merchant_name"),
                resultSet.getString("payment_method"),
                resultSet.getString("spending_type"),
                resultSet.getString("summary"),
                resultSet.getString("memo"),
                resultSet.getBoolean("hidden"),
                resultSet.getString("source_type"),
                toLocalDateTime(resultSet.getObject("created_at")),
                toLocalDateTime(resultSet.getObject("updated_at"))
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

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
