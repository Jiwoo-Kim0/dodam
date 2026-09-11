package com.ssafy.dodam.domain.account.dto.response;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.repository.AccountMonthlyFlowRow;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import java.util.List;

/**
 * 입출금 추이(GET /accounts/{accountNo}/trend). 기간 안의 모든 달을 오래된 순으로 채우고 거래가 없는 달은 0이다.
 */
public record AccountTrendResponse(
        Long accountId,
        String accountNoMasked,
        String startMonth,
        String endMonth,
        long totalIncome,
        long totalExpense,
        long totalNetChange,
        List<Month> months
) {

    public AccountTrendResponse {
        months = List.copyOf(months);
    }

    public static AccountTrendResponse from(Account account, YearMonthPeriod period, List<AccountMonthlyFlowRow> rows) {
        List<Month> months = rows.stream().map(Month::from).toList();
        return new AccountTrendResponse(
                account.getId(),
                account.getAccountNoMasked(),
                period.startMonth().toString(),
                period.endMonth().toString(),
                rows.stream().mapToLong(AccountMonthlyFlowRow::incomeTotal).sum(),
                rows.stream().mapToLong(AccountMonthlyFlowRow::expenseTotal).sum(),
                rows.stream().mapToLong(AccountMonthlyFlowRow::netChange).sum(),
                months
        );
    }

    public record Month(
            String yearMonth,
            long incomeTotal,
            long expenseTotal,
            long transferTotal,
            long netChange,
            long transactionCount
    ) {

        static Month from(AccountMonthlyFlowRow row) {
            return new Month(
                    row.yearMonth().toString(),
                    row.incomeTotal(),
                    row.expenseTotal(),
                    row.transferTotal(),
                    row.netChange(),
                    row.transactionCount()
            );
        }
    }
}
