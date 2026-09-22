package com.ssafy.dodam.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * <h2>635 추가 필드</h2>
 * <ul>
 *   <li>{@code cardSpending} — 그 달 카드 사용액(소비 정의 중 {@code card_id}가 있거나 {@code payment_method = CARD}인 거래).
 *       {@code totalSpending}의 부분집합이다</li>
 *   <li>{@code cardSpendingPreviousMonth}·{@code cardSpendingDifferenceFromPreviousMonth} — 전월 카드 사용액과 증감(부호 유지).
 *       전월 비교 규칙은 {@code previousMonth}와 같다(비교 대상 없으면 둘 다 null)</li>
 *   <li>{@code efficiency} — 금융 효율 점수(전체 + 6개 항목). 저장 형식이 깨졌으면 null</li>
 * </ul>
 * 635 이전에 만들어진 READY 리포트는 세 필드가 비어 있다가 {@code /reports/monthly} 조회 때 채워진다(합계는 그대로).
 *
 * <h2>697 추가 필드</h2>
 * <ul>
 *   <li>{@code metrics} — "주요 지표"(저축률·고정지출 비중·카드 사용액·대출 잔액, 전월 비교)와 "금융 생활 패턴"(저축률·소비 증가·현금흐름 위험 3장).
 *       정의는 {@link MonthlyReportMetricsResponse}. 697 이전 리포트는 첫 월간 조회 때 채워진다. 저장 형식이 깨졌으면 null</li>
 * </ul>
 *
 * @param diagnosisId    그 달 말일까지 완료된 가장 최근 진단(694). 없으면 null
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
        Long cardSpending,
        Long cardSpendingPreviousMonth,
        Long cardSpendingDifferenceFromPreviousMonth,
        EfficiencyScoreResponse efficiency,
        MonthlyReportMetricsResponse metrics,
        Long diagnosisId,
        String overallStatus,
        String unavailableReason
) {

    /**
     * `monthly_report.financial_summary`에 그대로 저장하는 부분. 응답의 합계 필드와 1:1이다.
     * 카드 사용액 3개(635)는 나중에 추가돼 이전 저장본에는 없다(null) — 조회 때 채운다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Financial(
            long totalIncome,
            long totalExpense,
            long totalSpending,
            long totalSaving,
            long netCashflow,
            long transactionCount,
            MonthAmounts previousMonth,
            MonthAmounts changeFromPreviousMonth,
            Long cardSpending,
            Long cardSpendingPreviousMonth,
            Long cardSpendingDifferenceFromPreviousMonth
    ) {

        public boolean hasCardSpending() {
            return cardSpending != null;
        }

        /** 저장된 합계는 그대로 두고 카드 사용액만 붙인 복사본(635 이전 리포트 보강용). */
        public Financial withCardSpending(Long current, Long previous, Long difference) {
            return new Financial(totalIncome, totalExpense, totalSpending, totalSaving, netCashflow, transactionCount,
                    previousMonth, changeFromPreviousMonth, current, previous, difference);
        }
    }

    /** 전월 값과 전월 대비 증감에 같은 형식을 쓴다. 증감은 부호를 그대로 둔다. */
    public record MonthAmounts(long totalIncome, long totalExpense, long totalSpending, long totalSaving) {

        public MonthAmounts minus(MonthAmounts other) {
            return new MonthAmounts(totalIncome - other.totalIncome(), totalExpense - other.totalExpense(),
                    totalSpending - other.totalSpending(), totalSaving - other.totalSaving());
        }
    }
}
