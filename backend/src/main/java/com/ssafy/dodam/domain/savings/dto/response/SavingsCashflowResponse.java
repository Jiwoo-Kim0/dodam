package com.ssafy.dodam.domain.savings.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 저축을 반영한 월 현금흐름 부담과 가용금액. 부담 API와 가용금액 API가 같은 응답을 쓴다.
 *
 * @param baseDate 가용금액 API가 받은 기준일. 부담 API에서는 null이다.
 * @param loanRepaymentAmount 조회 월에 이미 상환한 금액
 * @param remainingLoanRepaymentAmount 조회 월에 남은 예정 상환액. 실패·연체 회차는 어느 쪽에도 넣지 않는다.
 * @param disposableAmount 소득 − (저축 + 고정지출 + 상환). 구성 요소를 하나라도 모르면 null이다.
 * @param negativeDisposable 가용금액이 음수인지. 계산하지 못했으면 null이다.
 */
public record SavingsCashflowResponse(
        YearMonth yearMonth, LocalDate asOfDate, LocalDate baseDate, Long monthlyIncome,
        Long fixedExpense, Long savingsAmount, Long loanRepaymentAmount, Long remainingLoanRepaymentAmount,
        Long disposableAmount, BigDecimal savingsBurdenRate, BigDecimal totalBurdenRate,
        Boolean negativeDisposable, boolean complete, List<String> reasons, List<String> assumptions
) {
}
