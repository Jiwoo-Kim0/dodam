package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import java.time.LocalDateTime;

/**
 * 금융 데이터 통합 요약 (로컬 DB 집계). 금액 단위는 원.
 *
 * 합산 규칙 (ERD 「데이터 무결성 규칙」)
 * - 마이너스통장 `balance`는 -사용액으로 저장되므로 총자산에서 빼고 부채(minusUsedAmount)로 옮긴다.
 * - CLOSED 계좌, COMPLETED·CLOSED 대출은 제외한다.
 */
public record FinanceSummaryResponse(
        Connection connection,
        Accounts accounts,
        Cards cards,
        Loans loans,
        Totals totals
) {

    public record Connection(
            FinanceConnectionStatus status,
            LocalDateTime lastSyncedAt
    ) {
    }

    /**
     * @param count            CLOSED 제외 전체 계좌 수 (마이너스통장 포함)
     * @param minusAccountCount 마이너스통장 수
     * @param minusUsedAmount  마이너스통장 사용액 합 (양수)
     */
    public record Accounts(
            long count,
            long demandDepositBalance,
            long depositBalance,
            long savingsBalance,
            long minusAccountCount,
            long minusUsedAmount
    ) {
    }

    public record Cards(
            long activeCount,
            long totalCount,
            long totalCreditLimit
    ) {
    }

    public record Loans(
            long outstandingCount,
            long overdueCount,
            long totalBalance
    ) {
    }

    /**
     * @param cashAssets  입출금 + 예금 + 적금 잔액
     * @param liabilities 대출 잔액 + 마이너스통장 사용액
     * @param netAssets   cashAssets - liabilities
     */
    public record Totals(
            long cashAssets,
            long liabilities,
            long netAssets
    ) {
    }
}
