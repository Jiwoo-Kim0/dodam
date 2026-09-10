package com.ssafy.dodam.domain.cashflow.dto;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import java.time.LocalDate;
import java.util.List;

/**
 * 일별 잔액 계산 결과.
 *
 * @param minimumBalance 예측 구간의 최소 예상잔액
 * @param firstShortageDate 잔액이 처음 음수가 되는 날. 부족 구간이 없으면 null이다
 */
public record CashflowBalanceProjection(
        List<CashflowDailyBalance> dailyBalances,
        long minimumBalance,
        LocalDate firstShortageDate
) {
    public CashflowBalanceProjection {
        dailyBalances = List.copyOf(dailyBalances);
    }
}
