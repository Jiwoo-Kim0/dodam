package com.ssafy.dodam.domain.minusaccount.repository;

import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountBalanceWriter implements AccountBalanceWriter {

    private static final String DEMAND_DEPOSIT = "DEMAND_DEPOSIT";

    // MINUS 계좌의 잔액은 chk_account_balance_by_type 때문에 0 이하여야 한다.
    private static final String UPDATE_MINUS_BALANCE_SQL = """
            UPDATE account
               SET balance = :balance,
                   updated_at = :now
             WHERE account_id = :accountId
               AND user_id = :userId
               AND account_type = 'MINUS'
            """;

    private static final String WITHDRAW_SQL = """
            UPDATE account
               SET balance = balance - :amount,
                   updated_at = :now
             WHERE account_id = :accountId
               AND user_id = :userId
               AND account_type = 'DEMAND_DEPOSIT'
               AND balance >= :amount
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public JdbcAccountBalanceWriter(
            NamedParameterJdbcTemplate jdbcTemplate,
            EntityManager entityManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<MinusAccountOwner> findWithdrawalAccountForUpdate(
            Long userId,
            String accountNoHash
    ) {
        // 두 계좌를 잠그는 순서를 서비스가 통제하므로 여기서는 잠금만 건다.
        List<MinusAccountOwner> found = entityManager.createQuery(
                        """
                        select a from MinusAccountOwner a
                         where a.userId = :userId
                           and a.accountNoHash = :accountNoHash
                           and a.accountType = :accountType
                        """,
                        MinusAccountOwner.class
                )
                .setParameter("userId", userId)
                .setParameter("accountNoHash", accountNoHash)
                .setParameter("accountType", DEMAND_DEPOSIT)
                .getResultList();
        return found.stream().findFirst();
    }

    @Override
    public List<MinusAccountOwner> findDemandDepositAccounts(Long userId) {
        return entityManager.createQuery(
                        """
                        select a from MinusAccountOwner a
                         where a.userId = :userId
                           and a.accountType = :accountType
                           and a.status = 'ACTIVE'
                        """,
                        MinusAccountOwner.class
                )
                .setParameter("userId", userId)
                .setParameter("accountType", DEMAND_DEPOSIT)
                .getResultList();
    }

    @Override
    public void updateMinusBalance(Long userId, Long accountId, long usedAmount, LocalDateTime now) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("balance", -usedAmount);
        parameters.put("accountId", accountId);
        parameters.put("userId", userId);
        parameters.put("now", now);

        int updated = jdbcTemplate.update(UPDATE_MINUS_BALANCE_SQL, parameters);
        if (updated != 1) {
            throw new IllegalStateException("마이너스통장 잔액을 갱신하지 못했습니다.");
        }
    }

    @Override
    public void withdraw(Long userId, Long accountId, long amount, LocalDateTime now) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("amount", amount);
        parameters.put("accountId", accountId);
        parameters.put("userId", userId);
        parameters.put("now", now);

        // 잔액 조건을 UPDATE에 포함해 서비스 검증과 실제 갱신 사이의 경쟁 조건까지 막는다.
        int updated = jdbcTemplate.update(WITHDRAW_SQL, parameters);
        if (updated != 1) {
            throw new IllegalStateException("출금계좌 잔액을 갱신하지 못했습니다.");
        }
    }
}
