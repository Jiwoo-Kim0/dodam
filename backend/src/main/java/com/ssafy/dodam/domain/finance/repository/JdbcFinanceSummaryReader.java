package com.ssafy.dodam.domain.finance.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ERD `account`, `card`, `loan` 기준 집계. 상태 코드는 ERD CHECK 값과 동일하게 쓴다.
 */
@Repository
public class JdbcFinanceSummaryReader implements FinanceSummaryReader {

    private static final String ACCOUNTS_BY_TYPE_SQL = """
            SELECT account_type, COUNT(*) AS cnt, COALESCE(SUM(balance), 0) AS total_balance
            FROM account
            WHERE user_id = ? AND status <> 'CLOSED'
            GROUP BY account_type
            """;

    private static final String CARDS_SQL = """
            SELECT COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_count,
                   COUNT(*) AS total_count,
                   COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN credit_limit ELSE 0 END), 0) AS total_credit_limit
            FROM card
            WHERE user_id = ?
            """;

    private static final String LOANS_SQL = """
            SELECT COUNT(*) AS outstanding_count,
                   COALESCE(SUM(CASE WHEN status = 'OVERDUE' THEN 1 ELSE 0 END), 0) AS overdue_count,
                   COALESCE(SUM(balance), 0) AS total_balance
            FROM loan
            WHERE user_id = ? AND status IN ('ACTIVE', 'OVERDUE')
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcFinanceSummaryReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AccountTypeSummaryRow> readAccountsByType(Long userId) {
        return jdbcTemplate.query(ACCOUNTS_BY_TYPE_SQL, (rs, rowNum) -> new AccountTypeSummaryRow(
                rs.getString("account_type"),
                rs.getLong("cnt"),
                rs.getLong("total_balance")
        ), userId);
    }

    @Override
    public CardSummaryRow readCards(Long userId) {
        return jdbcTemplate.queryForObject(CARDS_SQL, (rs, rowNum) -> new CardSummaryRow(
                rs.getLong("active_count"),
                rs.getLong("total_count"),
                rs.getLong("total_credit_limit")
        ), userId);
    }

    @Override
    public LoanSummaryRow readLoans(Long userId) {
        return jdbcTemplate.queryForObject(LOANS_SQL, (rs, rowNum) -> new LoanSummaryRow(
                rs.getLong("outstanding_count"),
                rs.getLong("overdue_count"),
                rs.getLong("total_balance")
        ), userId);
    }
}
