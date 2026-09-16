package com.ssafy.dodam.domain.report.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 데일리 브리핑(API 20 #1). 기본 기준일은 <b>어제</b>다 — 브리핑은 "전날에 대한" 요약이다(기획안).
 *
 * <p>합계 정의는 월간 리포트와 같다. {@code totalExpense = totalSpending + totalSaving}이고
 * {@code balanceChange = totalIncome − totalExpense}다. {@code topCategories}는 소비(506 정의)만 담으므로
 * 그 합은 {@code totalSpending}과 같고 {@code totalExpense}와는 다르다.
 *
 * <p>{@code coachMessage}는 <b>API 21의 응답이 아니다.</b> 브리핑 전용 문구가 생길 자리이며 당분간 null이다.
 * AI 문장이 필요하면 FE가 API 21을 따로 부른다.
 *
 * @param dataStatus         READY / NO_TRANSACTIONS / INSUFFICIENT_DATA
 * @param unavailableReason  READY면 null. null이 아니면 수치 대신 안내 문구를 띄운다
 */
public record DailyReportResponse(
        Long reportId,
        String type,
        String period,
        LocalDateTime generatedAt,
        String dataStatus,
        Long totalIncome,
        Long totalExpense,
        Long totalSpending,
        Long totalSaving,
        Long transactionCount,
        List<CategoryAmount> topCategories,
        Long balanceChange,
        List<NotableEvent> notableEvents,
        String coachMessage,
        String unavailableReason
) {

    public DailyReportResponse {
        topCategories = topCategories == null ? List.of() : List.copyOf(topCategories);
        notableEvents = notableEvents == null ? List.of() : List.copyOf(notableEvents);
    }

    /** `daily_report.financial_summary`에 그대로 저장하는 부분. 응답의 합계 필드와 1:1이다. */
    public record Financial(
            long totalIncome,
            long totalExpense,
            long totalSpending,
            long totalSaving,
            long transactionCount,
            long balanceChange,
            List<CategoryAmount> topCategories
    ) {

        public Financial {
            topCategories = topCategories == null ? List.of() : List.copyOf(topCategories);
        }
    }

    /** `daily_report.notable_events`에 저장하는 부분. 배열을 그대로 두지 않고 객체로 감싸 형식 변경에 여지를 둔다. */
    public record NotableEvents(List<NotableEvent> items) {

        public NotableEvents {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /**
     * @param categoryCode 미분류 거래는 {@code UNCATEGORIZED}
     */
    public record CategoryAmount(String categoryCode, String categoryName, long amount, long count) {
    }

    /**
     * 그날 실제로 관측된 사실만 담는다. 인과관계("어제 소비가 목표 달성일을 며칠 바꿨다")는 근거가 없으면 만들지 않는다
     * (docs/report-backend-handoff.md 데일리 브리핑 항목).
     *
     * <p>코드: {@code NO_SPENDING}(소비 0원), {@code INCOME_RECEIVED}(수입 발생),
     * {@code LARGE_EXPENSE}(최근 30일 일평균 소비를 넘는 단일 지출), {@code SUBSCRIPTION_CHARGED}(정기결제 결제).
     *
     * @param amount 그 사실에 딸린 금액. 금액이 없는 사실이면 null
     */
    public record NotableEvent(String code, String message, Long amount) {
    }
}
