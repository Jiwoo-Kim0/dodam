package com.ssafy.dodam.domain.account.dto.response;

/**
 * 계좌 요약(GET /accounts/summary). 금액 단위는 원.
 *
 * <p>Finance Data 통합 요약(500)의 `accounts` 절과 같은 합산 규칙을 쓴다: CLOSED 계좌는 개수·잔액에서 제외하고
 * 마이너스통장 `balance`(0 이하)는 사용액(양수)으로 뒤집어 부채로 본다.
 *
 * @param count           CLOSED 제외 전체 계좌 수 (마이너스통장 포함)
 * @param activeCount     ACTIVE 계좌 수
 * @param inactiveCount   INACTIVE 계좌 수
 * @param closedCount     CLOSED 계좌 수 (참고용, 다른 합계에는 들어가지 않음)
 * @param minusUsedAmount 마이너스통장 사용액 합 (양수)
 * @param totalAssets     입출금 + 예금 + 적금 잔액
 * @param netAssets       totalAssets − minusUsedAmount
 */
public record AccountSummaryResponse(
        long count,
        long activeCount,
        long inactiveCount,
        long closedCount,
        long demandDepositCount,
        long demandDepositBalance,
        long depositCount,
        long depositBalance,
        long savingsCount,
        long savingsBalance,
        long minusAccountCount,
        long minusUsedAmount,
        long totalAssets,
        long netAssets
) {
}
