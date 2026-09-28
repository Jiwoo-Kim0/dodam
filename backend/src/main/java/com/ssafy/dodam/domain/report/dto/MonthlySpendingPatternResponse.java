package com.ssafy.dodam.domain.report.dto;

import java.util.List;
import java.util.Map;

/**
 * 월간 소비패턴(API 20 #4). 월간 리포트를 만들 때 계산해 저장하고, 이 엔드포인트는 저장본을 읽는다.
 *
 * <p>{@code ratio}는 <b>0~1 소수 4자리</b>다. 퍼센트 변환은 FE가 한다(패턴 분석 API 3과 같은 규칙).
 *
 * <p>분모가 항목마다 다르다. 섞으면 비율의 합이 1을 넘는다.
 * <ul>
 *   <li>{@code byCategory[].ratio} — 분모는 {@code totalSpending}이다({@code ratioBase}로 명시한다).
 *       카테고리 집계는 소비(506 정의)만 담으므로 저축성 지출이 들어 있지 않다</li>
 *   <li>{@code bySpendingType} — 3개 키를 항상 포함하고 합은 {@code totalExpense}와 같다</li>
 *   <li>{@code weekdayWeekend} — 소비 기준이며 합은 {@code totalSpending}과 같다</li>
 * </ul>
 *
 * @param comparedToPrevious 전월 대비 소비 증감. {@code ratioDelta}는 증감률(전월 소비 대비, 0~1 소수 4자리)이며
 *                           전월 소비가 0이면 null이다
 */
public record MonthlySpendingPatternResponse(
        String yearMonth,
        long totalExpense,
        long totalSpending,
        String ratioBase,
        List<Category> byCategory,
        Map<String, Long> bySpendingType,
        WeekdayWeekend weekdayWeekend,
        Compared comparedToPrevious
) {

    public static final String RATIO_BASE = "TOTAL_SPENDING";

    public MonthlySpendingPatternResponse {
        byCategory = byCategory == null ? List.of() : List.copyOf(byCategory);
        bySpendingType = bySpendingType == null ? Map.of() : Map.copyOf(bySpendingType);
    }

    public record Category(String categoryCode, String categoryName, long amount, double ratio, long count) {
    }

    public record WeekdayWeekend(long weekday, long weekend) {
    }

    public record Compared(long amountDelta, Double ratioDelta) {
    }
}
