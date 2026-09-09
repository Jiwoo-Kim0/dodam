package com.ssafy.dodam.domain.loan.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import java.time.LocalDate;
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
public class JdbcLoanRepaymentReader implements LoanRepaymentReader {

    // 소유권은 호출 전에 loan 조회로 확인하므로 여기서는 대출 식별자로만 범위를 좁힌다.
    private static final String WHERE_CLAUSE = """
            WHERE repayment.loan_id = :loanId
              AND repayment.repayment_date BETWEEN :startDate AND :endDate
            """;

    private static final String CONTENT_SQL = """
            SELECT
                repayment.loan_repayment_id,
                repayment.repayment_date,
                repayment.amount,
                repayment.principal,
                repayment.interest,
                repayment.status,
                repayment.failure_reason
            FROM loan_repayment repayment
            """
            + WHERE_CLAUSE
            + """
            ORDER BY repayment.repayment_date DESC, repayment.loan_repayment_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM loan_repayment repayment
            """
            + WHERE_CLAUSE;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLoanRepaymentReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<LoanRepaymentRow> read(Long loanId, QueryPeriod period, Pageable pageable) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("loanId", loanId);
        parameters.put("startDate", period.startDate());
        parameters.put("endDate", period.endDate());

        long total = Objects.requireNonNullElse(
                jdbcTemplate.queryForObject(COUNT_SQL, parameters, Long.class),
                0L
        );
        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        parameters.put("limit", pageable.getPageSize());
        parameters.put("offset", pageable.getOffset());
        List<LoanRepaymentRow> rows = jdbcTemplate.query(
                CONTENT_SQL,
                parameters,
                (resultSet, rowNumber) -> new LoanRepaymentRow(
                        resultSet.getLong("loan_repayment_id"),
                        resultSet.getObject("repayment_date", LocalDate.class),
                        resultSet.getLong("amount"),
                        resultSet.getObject("principal", Long.class),
                        resultSet.getObject("interest", Long.class),
                        resultSet.getString("status"),
                        resultSet.getString("failure_reason")
                )
        );
        return new PageImpl<>(rows, pageable, total);
    }
}
