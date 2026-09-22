package com.ssafy.dodam.domain.goal.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 모으기 목표 저축속도의 원천 집계(S15P21D105-694, 702). 예·적금은 납입 이력, 입출금은 연결 계좌 순현금흐름을 읽는다.
 *
 * <p>거래의 {@code account_id}가 목표에 연결된 입출금 계좌이거나, {@code card_id}가 그 계좌에서 대금이 빠지는 카드
 * ({@code card.withdrawal_account_id})인 경우만 합산한다. 카드 승인 행은 계좌 없이 원장에 남지만 대금은 결국 출금 계좌에서 나가므로
 * 연결 계좌의 현금흐름으로 본다. 카드 대금 결제 자체는 {@code card_billing_statement}에만 있고 원장 행이 없어 승인과 이중으로
 * 세지 않는다. 해지된 카드도 과거 승인은 그 계좌에서 빠졌으므로 상태로 걸러내지 않는다. 연결하지 않은 계좌·카드의 거래는 특정
 * 목표에 귀속할 근거가 없어 제외하고, 사용자 소유 조건도 함께 두어 다른 사용자의 거래를 격리한다.
 */
@Repository
public class JdbcGoalPaceReader {

    private static final String PAID_PAYMENTS_SQL = """
            SELECT deposit_saving_id, COALESCE(SUM(amount), 0) AS total
            FROM savings_payment
            WHERE deposit_saving_id IN (:accountIds)
              AND status = 'PAID'
              AND payment_date BETWEEN :from AND :to
            GROUP BY deposit_saving_id
            """;

    private static final String TERMS_SQL = """
            SELECT account_id, monthly_amount, opening_date, expiry_date
            FROM deposit_saving
            WHERE account_id IN (:accountIds)
            """;

    // 연결 계좌 귀속: 계좌 거래이거나 그 계좌에서 대금이 빠지는 카드의 승인 거래. 두 조회가 같은 범위를 봐야 개월 수와 합이 맞는다.
    private static final String LINKED_ACCOUNT_CONDITION = """
              AND (entry.account_id IN (:accountIds)
                   OR entry.card_id IN (SELECT linked_card.card_id
                                        FROM card linked_card
                                        WHERE linked_card.user_id = :userId
                                          AND linked_card.withdrawal_account_id IN (:accountIds)))
            """;

    // 순현금흐름: 수입 − (지출 + 이체). 숨긴 거래는 다른 집계와 같이 제외한다. 금액은 항상 양수고 방향은 transaction_type이 정한다.
    private static final String NET_CASHFLOW_SQL = """
            SELECT COALESCE(SUM(CASE WHEN entry.transaction_type = 'INCOME' THEN entry.amount ELSE -entry.amount END), 0)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
            """ + LINKED_ACCOUNT_CONDITION + """
              AND entry.hidden = FALSE
              AND entry.transaction_date BETWEEN :from AND :to
            """;

    private static final String FIRST_TRANSACTION_SQL = """
            SELECT MIN(entry.transaction_date)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
            """ + LINKED_ACCOUNT_CONDITION + """
              AND entry.hidden = FALSE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGoalPaceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 계좌별 기간 내 PAID 납입 합. 납입이 없는 계좌는 키가 없다. */
    public Map<Long, Long> readPaidPaymentTotals(Collection<Long> accountIds, LocalDate from, LocalDate to) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> totals = new HashMap<>();
        jdbc.query(PAID_PAYMENTS_SQL,
                new MapSqlParameterSource("accountIds", accountIds).addValue("from", from).addValue("to", to),
                rs -> {
                    totals.put(rs.getLong("deposit_saving_id"), rs.getLong("total"));
                });
        return totals;
    }

    /** 예·적금 약정 정보. `deposit_saving` 행이 없는 계좌는 키가 없다. */
    public Map<Long, DepositSavingTerms> readTerms(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, DepositSavingTerms> terms = new HashMap<>();
        jdbc.query(TERMS_SQL, new MapSqlParameterSource("accountIds", accountIds), rs -> {
            terms.put(rs.getLong("account_id"), new DepositSavingTerms(
                    rs.getObject("monthly_amount", Long.class),
                    rs.getObject("opening_date", LocalDate.class),
                    rs.getObject("expiry_date", LocalDate.class)));
        });
        return terms;
    }

    public long readNetCashflow(Long userId, Collection<Long> accountIds, LocalDate from, LocalDate to) {
        if (accountIds.isEmpty()) {
            return 0L;
        }
        Long value = jdbc.queryForObject(NET_CASHFLOW_SQL,
                new MapSqlParameterSource("userId", userId).addValue("accountIds", accountIds)
                        .addValue("from", from).addValue("to", to),
                Long.class);
        return value == null ? 0L : value;
    }

    public Optional<LocalDate> readFirstTransactionDate(Long userId, Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jdbc.queryForObject(FIRST_TRANSACTION_SQL,
                new MapSqlParameterSource("userId", userId).addValue("accountIds", accountIds), LocalDate.class));
    }

    /**
     * @param monthlyAmount 약정 월 납입액. 예금이나 자유적립이면 null
     * @param openingDate   가입일. 이 날이 든 달부터 납입 이력이 있을 수 있다
     * @param expiryDate    만기일. 지났으면 더 이상 납입하지 않는다
     */
    public record DepositSavingTerms(Long monthlyAmount, LocalDate openingDate, LocalDate expiryDate) {
    }
}
