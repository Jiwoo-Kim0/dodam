package com.ssafy.dodam.domain.transaction.dto.response;

import java.util.List;

/**
 * 카테고리별 소비(GET /spending/categories). 소비 금액 내림차순. 미분류 거래는 `categoryId = null`, `code = "UNCATEGORIZED"`로 한 묶음이다.
 *
 * @param sharePercent totalSpending 대비 비중(소수 첫째 자리). 합계가 0이면 0
 */
public record CategorySpendingResponse(
        String yearMonth,
        long totalSpending,
        long transactionCount,
        List<Item> categories
) {

    public CategorySpendingResponse {
        categories = List.copyOf(categories);
    }

    public record Item(
            Long categoryId,
            String code,
            String name,
            long totalSpending,
            long transactionCount,
            double sharePercent
    ) {
    }
}
