package com.ssafy.dodam.domain.account.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 휴면성 계좌 탐지(GET /accounts/inactive). 기획안 「휴면성 소액계좌」 확정 규칙: 잔액 > 0 AND 최근 N개월간 입출금 거래 없음.
 *
 * @param criteria     적용한 기준. FE가 "최근 6개월간 거래가 없는 계좌 2개에 …" 문구를 만들 때 쓴다
 * @param count        탐지된 계좌 수
 * @param totalBalance 탐지된 계좌 잔액 합 (정리·이동 제안 금액)
 * @param accounts     거래 없는 기간이 긴 순서
 */
public record InactiveAccountsResponse(
        Criteria criteria,
        long count,
        long totalBalance,
        List<Item> accounts
) {

    public InactiveAccountsResponse {
        accounts = List.copyOf(accounts);
    }

    /**
     * @param inactiveMonths 이 개월 수 이상 거래가 없으면 휴면으로 본다
     * @param maxBalance     이 금액 이하만 "소액"으로 본다. null이면 잔액 상한 없음
     * @param asOfDate       판정 기준일. `lastActivityDate <= asOfDate − inactiveMonths`이면 휴면
     */
    public record Criteria(
            int inactiveMonths,
            Long maxBalance,
            LocalDate asOfDate
    ) {
    }

    /**
     * @param lastActivityDate 마지막 거래일. 거래 이력이 없으면 계좌 적재일(created_at)
     * @param inactiveMonths   기준일까지 거래 없이 지난 개월 수 (내림)
     */
    public record Item(
            Long accountId,
            String bankName,
            String accountNoMasked,
            String accountName,
            String status,
            long balance,
            LocalDate lastActivityDate,
            long inactiveMonths
    ) {
    }
}
