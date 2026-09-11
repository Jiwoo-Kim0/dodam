package com.ssafy.dodam.domain.account.dto.response;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.repository.AccountMonthlyFlowRow;

/**
 * 월별 입출금 요약(GET /accounts/{accountNo}/monthly-summary). 숨긴 거래는 제외한다.
 *
 * @param yearMonth        YYYY-MM
 * @param transferTotal    TRANSFER 거래 합. 방향이 없어 netChange에 넣지 않는다
 * @param netChange        incomeTotal − expenseTotal
 * @param transactionCount 입금 + 출금 + 이체 건수
 */
public record AccountMonthlySummaryResponse(
        Long accountId,
        String accountNoMasked,
        String yearMonth,
        long incomeTotal,
        long incomeCount,
        long expenseTotal,
        long expenseCount,
        long transferTotal,
        long transferCount,
        long netChange,
        long transactionCount
) {

    public static AccountMonthlySummaryResponse from(Account account, AccountMonthlyFlowRow row) {
        return new AccountMonthlySummaryResponse(
                account.getId(),
                account.getAccountNoMasked(),
                row.yearMonth().toString(),
                row.incomeTotal(),
                row.incomeCount(),
                row.expenseTotal(),
                row.expenseCount(),
                row.transferTotal(),
                row.transferCount(),
                row.netChange(),
                row.transactionCount()
        );
    }
}
