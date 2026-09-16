package com.ssafy.dodam.domain.report.dto;

import java.time.LocalDateTime;

/**
 * 월간 리포트(API 20 #2). 기본 기준월은 <b>직전 완료 월</b>이다.
 *
 * <h2>합계 정의(FE 협의 확정본)</h2>
 * <ul>
 *   <li>{@code totalExpense} — 숨기지 않은 EXPENSE 거래 전부. <b>저축성 지출을 포함</b>한다</li>
 *   <li>{@code totalSpending} — 506 소비 정의(EXPENSE에서 {@code NON_SPENDING} 제외). 소비 화면·패턴 API와 같은 숫자다</li>
 *   <li>{@code totalSaving} — 저축성 지출({@code NON_SPENDING}). {@code totalExpense}에 포함되며 이중 차감하지 않는다</li>
 *   <li>{@code totalExpense = totalSpending + totalSaving}, {@code netCashflow = totalIncome − totalExpense}</li>
 * </ul>
 * 선공유 계약은 {@code totalExpense}를 "소비 집계와 같은 규칙"이라고 적었지만 소비 집계는 저축성 지출을 빼므로
 * 그 문장과 "{@code totalSaving}은 {@code totalExpense}에 포함된다"가 동시에 성립하지 않았다. 두 정의를 모두
 * 내려 주는 쪽으로 확정하고 {@code totalSpending}을 추가했다.
 *
 * <p>{@code previousMonth}가 없으면(비교할 이전 달이 사용자 데이터 이전이면) {@code changeFromPreviousMonth}도
 * 함께 null이다. 이전 달이 존재하는데 거래가 0건이면 null이 아니라 0원이다.
 *
 * @param diagnosisId    그 달 안에 완료된 가장 최근 진단. 없으면 null
 * @param overallStatus  위 진단의 종합 상태(HEALTHY / CAUTION / RISK). 진단이 없으면 null
 */
public record MonthlyReportResponse(
        Long reportId,
        String type,
        String period,
        LocalDateTime generatedAt,
        String dataStatus,
        Long totalIncome,
        Long totalExpense,
        Long totalSpending,
        Long totalSaving,
        Long netCashflow,
        Long transactionCount,
        MonthAmounts previousMonth,
        MonthAmounts changeFromPreviousMonth,
        Long diagnosisId,
        String overallStatus,
        String unavailableReason
) {

    /** `monthly_report.financial_summary`에 그대로 저장하는 부분. 응답의 합계 필드와 1:1이다. */
    public record Financial(
            long totalIncome,
            long totalExpense,
            long totalSpending,
            long totalSaving,
            long netCashflow,
            long transactionCount,
            MonthAmounts previousMonth,
            MonthAmounts changeFromPreviousMonth
    ) {
    }

    /** 전월 값과 전월 대비 증감에 같은 형식을 쓴다. 증감은 부호를 그대로 둔다. */
    public record MonthAmounts(long totalIncome, long totalExpense, long totalSpending, long totalSaving) {

        public MonthAmounts minus(MonthAmounts other) {
            return new MonthAmounts(totalIncome - other.totalIncome(), totalExpense - other.totalExpense(),
                    totalSpending - other.totalSpending(), totalSaving - other.totalSaving());
        }
    }
}
