package com.ssafy.dodam.domain.account.dto.response;

import java.util.List;

/**
 * 현금성 자산 합계(GET /accounts/balance-summary). 기획안 9-2 "현금성 자산" = 즉시 인출 가능한 수시입출금(DEMAND_DEPOSIT) 잔액.
 * 예금·적금은 저축성 자산이라 넣지 않고, 마이너스통장은 부채라 넣지 않는다. CLOSED 계좌는 제외한다.
 *
 * @param totalCash    ACTIVE·INACTIVE 수시입출금 계좌 잔액 합
 * @param accountCount 합산에 들어간 계좌 수
 * @param accounts     잔액 내림차순. `sharePercent`는 totalCash 대비 비중(소수 첫째 자리), 합계가 0이면 0
 */
public record AccountBalanceSummaryResponse(
        long totalCash,
        long accountCount,
        List<Item> accounts
) {

    public AccountBalanceSummaryResponse {
        accounts = List.copyOf(accounts);
    }

    public record Item(
            Long accountId,
            String bankName,
            String accountNoMasked,
            String accountName,
            String status,
            long balance,
            double sharePercent
    ) {
    }
}
