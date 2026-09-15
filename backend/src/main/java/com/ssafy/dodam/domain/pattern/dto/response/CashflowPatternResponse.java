package com.ssafy.dodam.domain.pattern.dto.response;

import java.util.List;

/**
 * GET /api/v1/patterns/me/cashflow?months. 소득·소비·저축·상환의 월 흐름을 요청 시점에 다시 계산한다. 금액은 원.
 *
 * @param startMonth         기간 시작 달(yyyy-MM)
 * @param endMonth           기간 마지막 달
 * @param monthCount         기간 개월 수
 * @param monthsWithData     거래가 있는 달 수
 * @param incomeBasis        TRANSACTIONS | PROFILE | NONE
 * @param monthlyIncome      월 소득. NONE이면 null
 * @param averages           거래가 있는 달 기준 월평균
 * @param savingRate         월평균 저축성 지출 / 월 소득. 소득을 모르면 null
 * @param savingConsistency  저축성 지출이 있는 달 비율
 * @param cashflowStability  (소득 − 소비) ≥ 0인 달 비율. 소득을 모르면 null
 * @param debtBurden         월평균 대출상환 / 월 소득
 * @param liquidity          입출금 활성 계좌 잔액과 월평균 소비 대비 몇 달분인지. 계좌 정보 없으면 null
 * @param months             월별 흐름(오래된 순)
 */
public record CashflowPatternResponse(
        String startMonth,
        String endMonth,
        int monthCount,
        int monthsWithData,
        String incomeBasis,
        Long monthlyIncome,
        Averages averages,
        Double savingRate,
        Double savingConsistency,
        Double cashflowStability,
        Double debtBurden,
        Liquidity liquidity,
        List<Month> months
) {

    public CashflowPatternResponse {
        months = List.copyOf(months);
    }

    /** @param net income − spending(저축성 지출은 소비가 아니므로 빼지 않는다) */
    public record Averages(long income, long spending, long savingTransfer, long loanRepayment, long net) {
    }

    public record Liquidity(long liquidBalance, Double runwayMonths, String level) {
    }

    public record Month(
            String yearMonth,
            long income,
            long spending,
            long savingTransfer,
            long loanRepayment,
            long net,
            long transactionCount
    ) {
    }
}
