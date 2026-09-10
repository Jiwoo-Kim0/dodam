package com.ssafy.dodam.domain.cashflow.repository;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 예측의 시작 잔액 조회.
 *
 * <p>활성 수시입출금(`DEMAND_DEPOSIT`) 계좌만 더한다. 카드대금·대출상환·적금납입이 실제로 빠져나가는
 * 계좌이고 즉시 출금이 가능한 현금이기 때문이다.
 *
 * <ul>
 *   <li>`DEPOSIT`·`SAVINGS`: 만기 전 출금이 불가하거나 불이익이 있다. 더하면 실제보다 낙관적인
 *       잔액이 되어 없는 여유를 보고한다.
 *   <li>`MINUS`: `chk_account_balance_by_type`상 잔액이 0 이하인 사용액이며 현금이 아니다.
 *       한도 여력은 minusaccount 도메인 정보이므로 위험 판단(S15P21D105-542)에서 다룬다.
 *   <li>`INACTIVE`·`CLOSED`: 출금 경로로 쓸 수 없다고 보수적으로 본다.
 * </ul>
 *
 * <p>{@code FinanceSummaryReader}도 유형별 합계를 돌려주지만 자산 요약용 계약이고
 * `status <> 'CLOSED'`라 `INACTIVE`를 포함한다. 기준이 달라 재사용하지 않는다.
 */
@Repository
public class CashflowBalanceReader {

    private static final String AVAILABLE_BALANCE_SQL = """
            SELECT COALESCE(SUM(balance), 0)
              FROM account
             WHERE user_id = :userId
               AND status = 'ACTIVE'
               AND account_type = 'DEMAND_DEPOSIT'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CashflowBalanceReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 연동 계좌가 없으면 0원이며 오류가 아니다. */
    public long readStartingBalance(Long userId) {
        Long balance = jdbcTemplate.queryForObject(
                AVAILABLE_BALANCE_SQL, Map.of("userId", userId), Long.class);
        return balance == null ? 0L : balance;
    }
}
