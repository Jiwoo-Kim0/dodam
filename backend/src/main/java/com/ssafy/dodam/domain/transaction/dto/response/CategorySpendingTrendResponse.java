package com.ssafy.dodam.domain.transaction.dto.response;

import java.util.List;

/**
 * 카테고리 소비 추이(GET /spending/categories/trend). 기간 안의 모든 달을 오래된 순으로 채운다.
 *
 * @param category 카테고리 필터를 줬으면 그 코드, 아니면 null(전체)
 * @param months   달마다 소비 합과 카테고리별 소비(내림차순). 거래가 없는 달은 합 0, 빈 목록
 */
public record CategorySpendingTrendResponse(
        String startMonth,
        String endMonth,
        String category,
        long totalSpending,
        long monthlyAverage,
        List<Month> months
) {

    public CategorySpendingTrendResponse {
        months = List.copyOf(months);
    }

    public record Month(
            String yearMonth,
            long totalSpending,
            long transactionCount,
            List<Category> categories
    ) {

        public Month {
            categories = List.copyOf(categories);
        }
    }

    public record Category(
            Long categoryId,
            String code,
            String name,
            long totalSpending,
            long transactionCount
    ) {
    }
}
