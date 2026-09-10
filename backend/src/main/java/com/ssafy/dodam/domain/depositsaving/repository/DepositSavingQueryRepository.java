package com.ssafy.dodam.domain.depositsaving.repository;

import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Account 소관 엔티티를 중복 생성하지 않고 필요한 마스킹 컬럼만 조회한다. */
@Repository
public class DepositSavingQueryRepository {
    private static final String FROM = """
            FROM account a
            LEFT JOIN deposit_saving d
              ON d.account_id = a.account_id AND d.user_id = a.user_id
                 AND d.account_type = a.account_type
            WHERE a.user_id = :userId AND a.account_type IN ('DEPOSIT', 'SAVINGS')
            """;
    private static final String COLUMNS = """
            SELECT a.account_id, a.account_name, a.bank_code, a.account_no_masked,
                   a.account_type, a.status, a.balance, d.interest_rate,
                   d.opening_date, d.expiry_date, d.monthly_amount,
                   d.account_id AS detail_id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public DepositSavingQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<DepositSavingResponse> findAccounts(
            Long userId, String type, String status,
            LocalDate after, LocalDate through, Pageable pageable
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        String filter = status == null
                ? " AND a.status IN ('ACTIVE', 'INACTIVE')"
                : " AND a.status = :status";
        if (status != null) {
            params.addValue("status", status);
        }
        if (type != null) {
            filter += " AND a.account_type = :type";
            params.addValue("type", type);
        }
        if (after != null) {
            filter += " AND d.expiry_date > :after AND d.expiry_date <= :through";
            params.addValue("after", after).addValue("through", through);
        }
        long count = jdbc.queryForObject("SELECT COUNT(*) " + FROM + filter, params, Long.class);
        String order = after == null ? "a.account_id" : "d.expiry_date, a.account_id";
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<DepositSavingResponse> rows = jdbc.query(
                COLUMNS + FROM + filter + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> new DepositSavingResponse(
                        rs.getLong("account_id"), rs.getString("account_name"),
                        rs.getString("bank_code"), rs.getString("account_no_masked"),
                        rs.getString("account_type"), rs.getString("status"),
                        rs.getLong("balance"), rs.getBigDecimal("interest_rate"),
                        rs.getObject("opening_date", LocalDate.class),
                        rs.getObject("expiry_date", LocalDate.class),
                        rs.getObject("monthly_amount", Long.class),
                        rs.getObject("detail_id") != null
                ));
        return new PageImpl<>(rows, pageable, count);
    }

    public Map<String, BalanceAggregate> summarize(Long userId) {
        // MySQL SUM(BIGINT)는 DECIMAL이다. long 변환 전에 범위를 검사할 수 있게 보존한다.
        List<BalanceAggregate> rows = jdbc.query("""
                SELECT account_type, COUNT(*) AS count, SUM(balance) AS balance
                FROM account
                WHERE user_id = :userId AND account_type IN ('DEPOSIT', 'SAVINGS')
                  AND status IN ('ACTIVE', 'INACTIVE')
                GROUP BY account_type
                """, Map.of("userId", userId), (rs, rowNum) -> new BalanceAggregate(
                        rs.getString("account_type"), rs.getLong("count"), rs.getBigDecimal("balance")));
        Map<String, BalanceAggregate> result = new HashMap<>();
        rows.forEach(row -> result.put(row.type(), row));
        return result;
    }

    public BigDecimal sumPaid(Long userId, LocalDate start, LocalDate end) {
        // 현재 종료된 적금도 과거 납입 실적을 보존한다. 잔액과 납입 이력을 중복 합산하지 않는다.
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM savings_payment p
                JOIN deposit_saving d ON d.account_id = p.deposit_saving_id
                  AND d.account_type = p.account_type
                JOIN account a ON a.account_id = d.account_id AND a.user_id = d.user_id
                  AND a.account_type = d.account_type
                WHERE a.user_id = :userId AND a.account_type = 'SAVINGS'
                  AND p.status = 'PAID' AND p.payment_date BETWEEN :start AND :end
                """, new MapSqlParameterSource("userId", userId)
                .addValue("start", start).addValue("end", end), BigDecimal.class);
    }

    public record BalanceAggregate(String type, long count, BigDecimal balance) {
    }
}
