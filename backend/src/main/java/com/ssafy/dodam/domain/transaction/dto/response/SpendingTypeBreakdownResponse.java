package com.ssafy.dodam.domain.transaction.dto.response;

import java.util.List;

/**
 * 고정지출(GET /spending/fixed)·변동지출(GET /spending/variable). `spending_type`이 FIXED / VARIABLE인 소비를 기간별로 본다.
 *
 * @param spendingType            FIXED 또는 VARIABLE
 * @param totalAmount             기간 합
 * @param monthlyAverage          기간 개월 수로 나눈 월평균(반올림)
 * @param shareOfSpendingPercent  같은 기간 전체 소비 대비 비중(%). 전체 소비가 0이면 0
 * @param latestMonthAmount       종료월 금액
 * @param previousAverage         종료월을 뺀 나머지 달의 월평균. 기간이 1개월이면 null
 * @param changeRatePercent       (latestMonthAmount − previousAverage) / previousAverage (%). previousAverage가 없거나 0이면 null
 * @param months                  기간 안 모든 달(오래된 순, 거래 없는 달 0)
 * @param categories              기간 합 기준 카테고리 내림차순. 미분류는 UNCATEGORIZED 묶음
 */
public record SpendingTypeBreakdownResponse(
        String spendingType,
        String startMonth,
        String endMonth,
        long totalAmount,
        long monthlyAverage,
        double shareOfSpendingPercent,
        long latestMonthAmount,
        Long previousAverage,
        Double changeRatePercent,
        List<Month> months,
        List<Category> categories
) {

    public SpendingTypeBreakdownResponse {
        months = List.copyOf(months);
        categories = List.copyOf(categories);
    }

    public record Month(String yearMonth, long amount, long transactionCount) {
    }

    public record Category(Long categoryId, String code, String name, long amount, long transactionCount, double sharePercent) {
    }
}
