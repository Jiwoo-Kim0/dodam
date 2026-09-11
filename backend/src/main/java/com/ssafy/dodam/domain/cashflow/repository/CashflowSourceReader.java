package com.ssafy.dodam.domain.cashflow.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 예측 저장 Repository와 별개인 원본 조회. 모든 원본 조회에 사용자 조건을 포함한다. */
@Repository
public class CashflowSourceReader {
    private final NamedParameterJdbcTemplate jdbc;

    public CashflowSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Repayment> repayments(Long userId) {
        return jdbc.query("""
                SELECT r.loan_id, r.loan_repayment_id, r.repayment_date, r.amount, r.status
                FROM loan_repayment r JOIN loan l ON l.loan_id = r.loan_id
                WHERE l.user_id = :userId ORDER BY r.repayment_date, r.loan_repayment_id
                """, Map.of("userId", userId), (rs, n) -> new Repayment(rs.getLong("loan_id"),
                rs.getLong("loan_repayment_id"), rs.getObject("repayment_date", LocalDate.class),
                rs.getLong("amount"), rs.getString("status")));
    }

    public SubscriptionRows subscriptions(Long userId) {
        // 구독의 선행 마이그레이션이 없는 환경만 명시적으로 구별한다. SQL 장애를 빈 결과로 숨기지 않는다.
        Boolean exists = jdbc.getJdbcTemplate().execute((ConnectionCallback<Boolean>) connection ->
                tableExists(connection, "subscription"));
        if (!Boolean.TRUE.equals(exists)) {
            return new SubscriptionRows(false, List.of());
        }
        return new SubscriptionRows(true, jdbc.query("""
                SELECT s.subscription_id, s.card_id, s.payment_amount, s.billing_cycle,
                       s.payment_day, s.next_payment_date, s.end_date
                FROM subscription s
                WHERE s.user_id = :userId AND s.status = 'ACTIVE'
                ORDER BY s.subscription_id
                """, Map.of("userId", userId), (rs, n) -> new Subscription(
                rs.getString("subscription_id"), rs.getObject("card_id", Long.class),
                rs.getLong("payment_amount"), rs.getString("billing_cycle"),
                rs.getObject("payment_day", Integer.class),
                rs.getObject("next_payment_date", LocalDate.class), rs.getObject("end_date", LocalDate.class))));
    }

    private boolean tableExists(Connection connection, String name) throws java.sql.SQLException {
        for (String pattern : List.of(name, name.toUpperCase(java.util.Locale.ROOT))) {
            try (ResultSet tables = connection.getMetaData().getTables(
                    connection.getCatalog(), connection.getSchema(), pattern, new String[] {"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    public record Repayment(long loanId, long id, LocalDate date, long amount, String status) { }
    public record SubscriptionRows(boolean available, List<Subscription> rows) { }
    public record Subscription(String id, Long cardId, long amount, String cycle,
            Integer paymentDay, LocalDate nextDate, LocalDate endDate) { }
}
