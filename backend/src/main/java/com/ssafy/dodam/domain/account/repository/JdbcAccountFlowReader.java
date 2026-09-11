package com.ssafy.dodam.domain.account.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 계좌 입출금 월별 집계(S15P21D105-503). 카드 집계 reader와 같이 `transaction`을 JDBC로 읽고 숨긴 거래는 제외한다.
 * 거래가 없는 달은 행이 없으므로 빈 달 채우기는 서비스가 한다.
 */
@Repository
public class JdbcAccountFlowReader {

    // YEAR()/MONTH()는 MySQL과 H2(MODE=MySQL) 모두 지원한다.
    private static final String MONTHLY_FLOW_SQL = """
            SELECT
                YEAR(entry.transaction_date) AS flow_year,
                MONTH(entry.transaction_date) AS flow_month,
                COALESCE(SUM(CASE WHEN entry.transaction_type = 'INCOME' THEN entry.amount ELSE 0 END), 0) AS income_total,
                SUM(CASE WHEN entry.transaction_type = 'INCOME' THEN 1 ELSE 0 END) AS income_count,
                COALESCE(SUM(CASE WHEN entry.transaction_type = 'EXPENSE' THEN entry.amount ELSE 0 END), 0) AS expense_total,
                SUM(CASE WHEN entry.transaction_type = 'EXPENSE' THEN 1 ELSE 0 END) AS expense_count,
                COALESCE(SUM(CASE WHEN entry.transaction_type = 'TRANSFER' THEN entry.amount ELSE 0 END), 0) AS transfer_total,
                SUM(CASE WHEN entry.transaction_type = 'TRANSFER' THEN 1 ELSE 0 END) AS transfer_count
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.account_id = :accountId
              AND entry.hidden = FALSE
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            GROUP BY YEAR(entry.transaction_date), MONTH(entry.transaction_date)
            ORDER BY flow_year, flow_month
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAccountFlowReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param startDate 시작일(포함), 보통 시작월의 1일
     * @param endDate   종료일(포함), 보통 종료월의 말일
     */
    public List<AccountMonthlyFlowRow> readMonthlyFlows(Long userId, Long accountId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(
                MONTHLY_FLOW_SQL,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("accountId", accountId)
                        .addValue("startDate", startDate)
                        .addValue("endDate", endDate),
                (resultSet, rowNumber) -> new AccountMonthlyFlowRow(
                        YearMonth.of(resultSet.getInt("flow_year"), resultSet.getInt("flow_month")),
                        resultSet.getLong("income_total"),
                        resultSet.getLong("income_count"),
                        resultSet.getLong("expense_total"),
                        resultSet.getLong("expense_count"),
                        resultSet.getLong("transfer_total"),
                        resultSet.getLong("transfer_count")
                )
        );
    }
}
