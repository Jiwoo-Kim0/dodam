package com.ssafy.dodam.domain.installment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 할부 부담률. 기획안 정의 {@code 월 할부 예정금액 / 월 예상 가용금액}을 따른다.
 *
 * @param scheduledAmount 대상 월의 할부 예정액. 현재 월은 기준일 이후 남은 회차만 더한다
 * @param complete 계산 제외 할부가 없으면 true. false면 예정액이 실제보다 작을 수 있다
 * @param availableAmount 할부 차감 전 월 예상 가용금액. 산출하지 못하면 null
 * @param burdenRatePercent {@code scheduledAmount / availableAmount × 100}, 소수 둘째 자리 HALF_UP. 미산출이면 null
 * @param unavailableReason 정상은 null. {@code INCOMPLETE_SCHEDULE}, {@code AVAILABLE_AMOUNT_UNAVAILABLE},
 *     {@code NON_POSITIVE_AVAILABLE_AMOUNT} 순으로 먼저 해당하는 사유 하나만 담는다
 */
public record InstallmentBurdenResponse(
        LocalDate asOfDate,
        String yearMonth,
        LocalDate periodStart,
        LocalDate periodEnd,
        long scheduledAmount,
        long installmentCount,
        boolean complete,
        long excludedInstallmentCount,
        List<InstallmentExclusionResponse> exclusions,
        Long availableAmount,
        AvailableAmountBasis availableAmountBasis,
        BigDecimal burdenRatePercent,
        String unavailableReason
) {
    public static final String INCOMPLETE_SCHEDULE = "INCOMPLETE_SCHEDULE";
    public static final String AVAILABLE_AMOUNT_UNAVAILABLE = "AVAILABLE_AMOUNT_UNAVAILABLE";
    public static final String NON_POSITIVE_AVAILABLE_AMOUNT = "NON_POSITIVE_AVAILABLE_AMOUNT";

    public InstallmentBurdenResponse {
        exclusions = List.copyOf(exclusions);
    }

    /**
     * 가용금액 산출 근거. {@code monthlyIncome − fixedExpense − monthlySaving − monthlyLoanRepayment}.
     *
     * @param monthlyIncome 재무 프로필의 월 소득. 없으면 null이고 가용금액도 산출하지 않는다
     * @param fixedExpense 재무 프로필의 월 고정지출. 없으면 0으로 두고 {@code notes}에 남긴다
     * @param monthlySaving 정상 적금의 약정 월 납입액 합
     * @param monthlyLoanRepayment 상환 중인 대출의 다음 회차 상환액 합
     * @param notes 값이 없어 대체한 항목의 코드 목록
     */
    public record AvailableAmountBasis(
            Long monthlyIncome,
            long fixedExpense,
            long monthlySaving,
            long monthlyLoanRepayment,
            List<String> notes
    ) {
        public static final String INCOME_UNKNOWN = "INCOME_UNKNOWN";
        public static final String FIXED_EXPENSE_UNKNOWN = "FIXED_EXPENSE_UNKNOWN";
        public static final String SAVING_MONTHLY_AMOUNT_MISSING = "SAVING_MONTHLY_AMOUNT_MISSING";

        public AvailableAmountBasis {
            notes = List.copyOf(notes);
        }
    }
}
