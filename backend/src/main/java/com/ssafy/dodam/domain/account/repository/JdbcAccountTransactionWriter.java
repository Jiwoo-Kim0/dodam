package com.ssafy.dodam.domain.account.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 금융망 계좌 거래를 `transaction` 원장에 적재한다. 거래 도메인(API 6, S15P21D105-504)이 `Transaction` 엔티티를
 * 소유하므로 여기서는 쓰기 전용 JDBC 어댑터만 둔다(마이너스통장의 `JdbcAccountBalanceWriter`와 같은 이유).
 *
 * <p>금융망 거래는 불변이므로 **없는 거래만 INSERT**한다. 이미 있는 거래는 사용자가 메모·카테고리·숨김을 바꿨을 수
 * 있어 어떤 컬럼도 덮어쓰지 않는다.
 */
@Repository
public class JdbcAccountTransactionWriter {

    private static final String EXISTING_IDS_SQL = """
            SELECT transaction_id
            FROM `transaction`
            WHERE user_id = :userId
              AND transaction_id IN (:transactionIds)
            """;

    private static final String INSERT_SQL = """
            INSERT INTO `transaction` (
                transaction_id, user_id, account_id, card_id, category_id,
                transaction_date, transaction_time, transaction_type, amount, balance_after,
                merchant_name, payment_method, spending_type, summary, memo,
                hidden, internal_transfer, source_type, created_at, updated_at
            ) VALUES (
                :transactionId, :userId, :accountId, NULL, :categoryId,
                :transactionDate, :transactionTime, :transactionType, :amount, :balanceAfter,
                NULL, 'ACCOUNT', :spendingType, :summary, :memo,
                FALSE, :internalTransfer, 'SSAFY', :createdAt, NULL
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String MARK_INTERNAL_TRANSFER_SQL = """
            UPDATE `transaction`
            SET internal_transfer = TRUE
            WHERE user_id = :userId
              AND transaction_id IN (:transactionIds)
              AND internal_transfer = FALSE
            """;

    public JdbcAccountTransactionWriter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return 실제로 INSERT한 건수
     */
    public int insertMissing(Long userId, Long accountId, List<NewTransaction> transactions, LocalDateTime now) {
        if (transactions.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(jdbcTemplate.queryForList(
                EXISTING_IDS_SQL,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("transactionIds", transactions.stream().map(NewTransaction::transactionId).toList()),
                String.class
        ));

        List<String> confirmedTransfers = transactions.stream()
                .filter(NewTransaction::internalTransfer)
                .map(NewTransaction::transactionId)
                .toList();
        if (!confirmedTransfers.isEmpty()) {
            // 재동기화는 사용자가 편집한 필드를 보존하면서 식별 결과만 보강한다.
            jdbcTemplate.update(MARK_INTERNAL_TRANSFER_SQL,
                    new MapSqlParameterSource("userId", userId)
                            .addValue("transactionIds", confirmedTransfers));
        }

        List<MapSqlParameterSource> batch = transactions.stream()
                .filter(transaction -> !existing.contains(transaction.transactionId()))
                .map(transaction -> new MapSqlParameterSource()
                        .addValue("transactionId", transaction.transactionId())
                        .addValue("userId", userId)
                        .addValue("accountId", accountId)
                        .addValue("transactionDate", transaction.transactionDate())
                        .addValue("transactionTime", transaction.transactionTime())
                        .addValue("transactionType", transaction.transactionType())
                        .addValue("amount", transaction.amount())
                        .addValue("balanceAfter", transaction.balanceAfter())
                        .addValue("categoryId", transaction.categoryId())
                        .addValue("spendingType", transaction.spendingType())
                        .addValue("summary", transaction.summary())
                        .addValue("memo", transaction.memo())
                        .addValue("internalTransfer", transaction.internalTransfer())
                        .addValue("createdAt", now))
                .toList();
        if (batch.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, batch.toArray(MapSqlParameterSource[]::new));
        return batch.size();
    }

    /**
     * 적재할 거래 한 건. 값 해석(날짜·유형·금액 검증)은 호출자가 끝낸 상태다.
     *
     * @param transactionType ERD `transaction_type` 값 (INCOME / EXPENSE / TRANSFER)
     */
    public record NewTransaction(
            String transactionId,
            LocalDate transactionDate,
            LocalTime transactionTime,
            String transactionType,
            long amount,
            Long balanceAfter,
            String summary,
            String memo,
            Long categoryId,
            String spendingType,
            boolean internalTransfer
    ) {
        public NewTransaction(String transactionId, LocalDate transactionDate, LocalTime transactionTime,
                String transactionType, long amount, Long balanceAfter, String summary, String memo,
                Long categoryId, String spendingType) {
            this(transactionId, transactionDate, transactionTime, transactionType, amount, balanceAfter,
                    summary, memo, categoryId, spendingType, false);
        }

        public NewTransaction(String transactionId, LocalDate transactionDate, LocalTime transactionTime,
                String transactionType, long amount, Long balanceAfter, String summary, String memo) {
            this(transactionId, transactionDate, transactionTime, transactionType, amount, balanceAfter,
                    summary, memo, null, null, false);
        }

        public NewTransaction asInternalTransfer() {
            return new NewTransaction(transactionId, transactionDate, transactionTime, transactionType, amount,
                    balanceAfter, summary, memo, categoryId, spendingType, true);
        }
    }
}
