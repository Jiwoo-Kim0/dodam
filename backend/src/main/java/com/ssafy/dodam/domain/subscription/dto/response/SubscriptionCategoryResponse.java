package com.ssafy.dodam.domain.subscription.dto.response;

/**
 * 정기결제 카테고리 한 건(GET /subscriptions/categories).
 *
 * @param subscriptionCount 이 카테고리에 있는 본인 정기결제 수(CANCELED 제외). 필터 칩 표시용
 */
public record SubscriptionCategoryResponse(
        String code,
        String label,
        int subscriptionCount
) {
}
