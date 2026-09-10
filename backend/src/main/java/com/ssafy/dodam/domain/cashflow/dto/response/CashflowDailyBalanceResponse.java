package com.ssafy.dodam.domain.cashflow.dto.response;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import java.time.LocalDate;

/** 날짜별 예상잔액 1건. 마이너스 구간을 그대로 보여야 하므로 음수를 0으로 절삭하지 않는다. */
public record CashflowDailyBalanceResponse(LocalDate balanceDate, Long expectedBalance) {

    public static CashflowDailyBalanceResponse from(CashflowDailyBalance balance) {
        return new CashflowDailyBalanceResponse(balance.getBalanceDate(), balance.getExpectedBalance());
    }
}
