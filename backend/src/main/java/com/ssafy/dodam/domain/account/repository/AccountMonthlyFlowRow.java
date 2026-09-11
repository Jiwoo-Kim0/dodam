package com.ssafy.dodam.domain.account.repository;

import java.time.YearMonth;

/**
 * 계좌 한 곳의 한 달 입출금 집계. 숨긴 거래는 제외한 값이다.
 *
 * @param transferTotal TRANSFER 거래 합. ERD상 TRANSFER는 방향이 없어 입금·출금 어느 쪽에도 넣지 않고 따로 보고한다.
 */
public record AccountMonthlyFlowRow(
        YearMonth yearMonth,
        long incomeTotal,
        long incomeCount,
        long expenseTotal,
        long expenseCount,
        long transferTotal,
        long transferCount
) {

    public static AccountMonthlyFlowRow empty(YearMonth yearMonth) {
        return new AccountMonthlyFlowRow(yearMonth, 0, 0, 0, 0, 0, 0);
    }

    public long netChange() {
        return incomeTotal - expenseTotal;
    }

    public long transactionCount() {
        return incomeCount + expenseCount + transferCount;
    }
}
