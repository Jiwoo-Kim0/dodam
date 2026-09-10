package com.ssafy.dodam.domain.savings.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 적금 조회 경계.
 *
 * <p>535·536과 같은 이유로 Account 소관 엔티티를 중복 생성하지 않고 필요한 컬럼만 JDBC로 읽는다.
 *
 * <p><b>소유권은 조회 조건에 넣는다.</b> 모든 질의가 {@code a.user_id}와 {@code account_type = 'SAVINGS'}를
 * 함께 걸어, 타인 계좌·없는 계좌·적금이 아닌 계좌가 모두 결과 0건이 되게 한다.
 */
@Repository
public class SavingsQueryRepository {
    private static final String ACCOUNT_FROM = """
            FROM account a LEFT JOIN deposit_saving d
              ON d.account_id = a.account_id AND d.user_id = a.user_id
              AND d.account_type = a.account_type
            WHERE a.user_id = :userId AND a.account_type = 'SAVINGS'
            """;
    private static final String ACCOUNT_COLUMNS = """
            SELECT a.account_id, a.account_name, a.account_no_masked, a.bank_code, a.bank_name,
                   a.status, a.balance, d.account_id AS detail_id, d.interest_rate,
                   d.monthly_amount, d.payment_day, d.next_payment_date,
                   d.opening_date, d.expiry_date, d.financial_product_id
            """;
    private static final String PAYMENT_FROM = """
            FROM savings_payment p
            JOIN deposit_saving d ON d.account_id = p.deposit_saving_id AND d.account_type = p.account_type
            JOIN account a ON a.account_id = d.account_id AND a.user_id = d.user_id
              AND a.account_type = d.account_type
            WHERE a.user_id = :userId AND a.account_type = 'SAVINGS'
            """;
    private static final String PAYMENT_COLUMNS = """
            SELECT p.savings_payment_id, p.deposit_saving_id, p.payment_date,
                   p.amount, p.installment_number, p.status
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SavingsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SavingsAccountRow> findOwned(Long userId, String hash) {
        return jdbc.query(ACCOUNT_COLUMNS + ACCOUNT_FROM + " AND a.account_no_hash = :hash",
                Map.of("userId", userId, "hash", hash), (rs, n) -> account(rs)).stream().findFirst();
    }

    public List<SavingsAccountRow> findAllOwned(Long userId) {
        return jdbc.query(ACCOUNT_COLUMNS + ACCOUNT_FROM + " ORDER BY a.account_id",
                Map.of("userId", userId), (rs, n) -> account(rs));
    }

    /**
     * 본인 적금의 납입 이력 전량.
     *
     * <p>일정·예상금액·월 저축액이 같은 목록을 여러 번 재사용하므로 요청당 한 번만 읽는다. 시연 데이터의
     * 사용자당 납입 회차는 한 자릿수라 기간을 좁혀 다시 조회하는 이점이 없다.
     */
    public List<SavingsPaymentRow> findPayments(Long userId) {
        return jdbc.query(PAYMENT_COLUMNS + PAYMENT_FROM + " ORDER BY p.payment_date, p.savings_payment_id",
                Map.of("userId", userId), (rs, n) -> payment(rs));
    }

    public Page<SavingsPaymentRow> findPayments(
            Long userId, Long accountId, LocalDate start, LocalDate end, Pageable page
    ) {
        String filter = " AND a.account_id = :id AND p.payment_date BETWEEN :start AND :end";
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("id", accountId)
                .addValue("start", start).addValue("end", end);
        long count = jdbc.queryForObject("SELECT COUNT(*) " + PAYMENT_FROM + filter, params, Long.class);
        if (page.getOffset() >= count) {
            return new PageImpl<>(List.of(), page, count);
        }
        params.addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        List<SavingsPaymentRow> rows = jdbc.query(PAYMENT_COLUMNS + PAYMENT_FROM + filter
                + " ORDER BY p.payment_date DESC, p.savings_payment_id DESC LIMIT :limit OFFSET :offset",
                params, (rs, n) -> payment(rs));
        return new PageImpl<>(rows, page, count);
    }

    /**
     * 월 소득·고정지출. 행이 없거나 값이 NULL이면 빈 결과이며 0으로 대체하지 않는다.
     *
     * <p>0원과 "등록하지 않음"은 다른 상태다. 0으로 바꾸면 소득 미등록 사용자의 부담률이 계산 가능한
     * 값처럼 보인다.
     */
    public Optional<IncomeProfile> findIncomeProfile(Long userId) {
        return jdbc.query(
                "SELECT monthly_income, fixed_expense FROM financial_profile WHERE user_id = :userId",
                Map.of("userId", userId), (rs, n) -> new IncomeProfile(
                        rs.getObject("monthly_income", Long.class),
                        rs.getObject("fixed_expense", Long.class))).stream().findFirst();
    }

    /**
     * 조회 기간의 대출 상환액을 상태별로 합산한다.
     *
     * <p><b>예정 이벤트를 중복 반영하지 않기 위해</b> 이미 낸 금액과 남은 예정액을 상태별로 나눠 돌려준다.
     * 호출부가 어떤 상태를 합칠지 정한다. 실패·연체 회차는 호출부가 제외한다.
     *
     * <p>MySQL {@code SUM(BIGINT)}는 DECIMAL이라 long 변환 전에 범위를 확인할 수 있게 보존한다.
     */
    public Map<String, BigDecimal> sumLoanRepaymentsByStatus(Long userId, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> totals = new HashMap<>();
        jdbc.query("""
                SELECT r.status AS status, SUM(r.amount) AS total
                FROM loan_repayment r
                JOIN loan l ON l.loan_id = r.loan_id
                WHERE l.user_id = :userId AND r.repayment_date BETWEEN :start AND :end
                GROUP BY r.status
                """,
                new MapSqlParameterSource("userId", userId).addValue("start", start).addValue("end", end),
                rs -> {
                    totals.put(rs.getString("status"), rs.getBigDecimal("total"));
                });
        return totals;
    }

    /** 월 소득과 고정지출. 등록하지 않은 항목은 null이다. */
    public record IncomeProfile(Long monthlyIncome, Long fixedExpense) {
    }

    private SavingsAccountRow account(ResultSet rs) throws SQLException {
        return new SavingsAccountRow(rs.getLong("account_id"), rs.getString("account_name"),
                rs.getString("account_no_masked"), rs.getString("bank_code"), rs.getString("bank_name"),
                rs.getString("status"),
                rs.getLong("balance"), rs.getObject("detail_id") != null, rs.getBigDecimal("interest_rate"),
                rs.getObject("monthly_amount", Long.class), rs.getObject("payment_day", Integer.class),
                rs.getObject("next_payment_date", LocalDate.class), rs.getObject("opening_date", LocalDate.class),
                rs.getObject("expiry_date", LocalDate.class), rs.getObject("financial_product_id", Long.class));
    }

    private SavingsPaymentRow payment(ResultSet rs) throws SQLException {
        return new SavingsPaymentRow(rs.getLong("savings_payment_id"), rs.getLong("deposit_saving_id"),
                rs.getObject("payment_date", LocalDate.class), rs.getLong("amount"),
                rs.getInt("installment_number"), rs.getString("status"));
    }
}
