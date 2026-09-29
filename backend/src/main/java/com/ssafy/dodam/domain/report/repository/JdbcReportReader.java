package com.ssafy.dodam.domain.report.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 리포트가 추가로 필요한 읽기 전용 집계. 소비·수입·저축성 지출 합계는 이미 있는
 * {@code JdbcSpendingAnalysisReader}·{@code JdbcSpendingReader}를 쓰고, 여기에는 그쪽에 없는 것만 둔다.
 * 같은 숫자를 두 곳에서 정의하지 않기 위해서다.
 */
@Repository
public class JdbcReportReader {

    // 거래 건수: 숨긴 거래와 TRANSFER(내 계좌 간 이동)는 세지 않는다. 브리핑의 "몇 건 썼나"에 이체는 포함되지 않는다.
    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_type IN ('INCOME', 'EXPENSE')
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String FIRST_TRANSACTION_SQL = """
            SELECT MIN(entry.transaction_date)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
            """;

    // 그날 가장 큰 단일 소비 1건. 소비 정의는 506과 같다(NON_SPENDING·숨긴 거래 제외).
    private static final String LARGEST_EXPENSE_SQL = """
            SELECT entry.amount, entry.merchant_name
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_type = 'EXPENSE'
              AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING')
              AND entry.transaction_date = :date
            ORDER BY entry.amount DESC, entry.transaction_id
            LIMIT 1
            """;

    private static final String SUBSCRIPTION_CHARGE_SQL = """
            SELECT s.name AS service_name, h.payment_amount
            FROM subscription_payment_history h
            JOIN subscription s ON s.subscription_id = h.subscription_id
            WHERE s.user_id = :userId
              AND h.payment_date = :date
            ORDER BY h.payment_amount DESC, h.payment_history_id
            """;

    // 카드 사용액(635): 소비 정의(506, NON_SPENDING·숨긴 거래 제외) 중 카드로 결제한 것. 카드 원장에서 온 거래는
    // card_id가 있고, 수동·seed 거래는 payment_method = 'CARD'다. 둘 중 하나면 카드 사용이다.
    // 결제수단별 소비 API(/spending/payment-methods)의 'CARD' 묶음과 card_id 기준 카드 화면 합계를 합친 정의다.
    private static final String CARD_SPENDING_SQL = """
            SELECT COALESCE(SUM(entry.amount), 0)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_type = 'EXPENSE'
              AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING')
              AND entry.transaction_date BETWEEN :startDate AND :endDate
              AND (entry.card_id IS NOT NULL OR UPPER(entry.payment_method) = 'CARD')
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcReportReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countTransactions(Long userId, LocalDate startDate, LocalDate endDate) {
        return Objects.requireNonNullElse(jdbcTemplate.queryForObject(COUNT_SQL,
                new MapSqlParameterSource().addValue("userId", userId)
                        .addValue("startDate", startDate).addValue("endDate", endDate), Long.class), 0L);
    }

    /** 그 기간의 카드 사용액(소비 정의 + 카드 결제). 거래가 없으면 0이다. */
    public long readCardSpending(Long userId, LocalDate startDate, LocalDate endDate) {
        return Objects.requireNonNullElse(jdbcTemplate.queryForObject(CARD_SPENDING_SQL,
                new MapSqlParameterSource().addValue("userId", userId)
                        .addValue("startDate", startDate).addValue("endDate", endDate), Long.class), 0L);
    }

    /** 사용자의 가장 이른 거래일. 거래가 없으면 비어 있다. 전월 비교 대상이 존재할 수 있는지 판단하는 데 쓴다. */
    public Optional<LocalDate> findFirstTransactionDate(Long userId) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(FIRST_TRANSACTION_SQL,
                new MapSqlParameterSource("userId", userId), LocalDate.class));
    }

    public Optional<Expense> findLargestExpense(Long userId, LocalDate date) {
        return jdbcTemplate.query(LARGEST_EXPENSE_SQL,
                new MapSqlParameterSource().addValue("userId", userId).addValue("date", date),
                (rs, i) -> new Expense(rs.getLong("amount"), rs.getString("merchant_name"))).stream().findFirst();
    }

    public List<SubscriptionCharge> findSubscriptionCharges(Long userId, LocalDate date) {
        return jdbcTemplate.query(SUBSCRIPTION_CHARGE_SQL,
                new MapSqlParameterSource().addValue("userId", userId).addValue("date", date),
                (rs, i) -> new SubscriptionCharge(rs.getString("service_name"), rs.getLong("payment_amount")));
    }

    /** @param merchantName 가맹점명이 없으면 null */
    public record Expense(long amount, String merchantName) {
    }

    public record SubscriptionCharge(String serviceName, long amount) {
    }
}
