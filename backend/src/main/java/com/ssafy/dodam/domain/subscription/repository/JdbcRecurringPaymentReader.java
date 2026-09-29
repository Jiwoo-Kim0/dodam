package com.ssafy.dodam.domain.subscription.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 미등록 반복결제 탐지(S15P21D105-513)용 거래 reader. 거래 조회(504)의 {@code JdbcTransactionReader}와 같은 방식으로 `transaction`을
 * 직접 읽되, 가맹점명이 있는 EXPENSE·숨김 아님 거래만 기간 안에서 가져온다. 저축·이체 같은 NON_SPENDING 거래는 소비가 아니므로 뺀다
 * (seed의 매월 저축 이체가 반복결제로 잡히는 것을 막는다). 묶기(가맹점·금액)와 주기 판정은 서비스가 메모리에서 한다
 * (사용자 한 명의 기간 내 거래는 수백 건 수준).
 */
@Repository
public class JdbcRecurringPaymentReader {

    private static final String SQL = """
            SELECT
                entry.transaction_id,
                entry.card_id,
                entry.transaction_date,
                entry.amount,
                entry.merchant_name,
                category.code AS category_code
            FROM `transaction` entry
            LEFT JOIN transaction_category category
                ON category.category_id = entry.category_id
            WHERE entry.user_id = :userId
              AND entry.transaction_type = 'EXPENSE'
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING')
              AND entry.merchant_name IS NOT NULL
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            ORDER BY entry.merchant_name, entry.amount, entry.transaction_date, entry.transaction_id
            """;

    private static final RowMapper<RecurringPaymentRow> ROW_MAPPER = JdbcRecurringPaymentReader::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRecurringPaymentReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecurringPaymentRow> read(Long userId, LocalDate startDate, LocalDate endDate) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);
        return jdbcTemplate.query(SQL, parameters, ROW_MAPPER);
    }

    private static RecurringPaymentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        long cardId = rs.getLong("card_id");
        boolean cardNull = rs.wasNull();
        return new RecurringPaymentRow(
                rs.getString("transaction_id"),
                cardNull ? null : cardId,
                rs.getObject("transaction_date", LocalDate.class),
                rs.getLong("amount"),
                rs.getString("merchant_name"),
                rs.getString("category_code")
        );
    }
}
