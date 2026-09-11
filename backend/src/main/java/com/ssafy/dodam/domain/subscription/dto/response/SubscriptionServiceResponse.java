package com.ssafy.dodam.domain.subscription.dto.response;

import com.ssafy.dodam.domain.subscription.service.SubscriptionServiceCatalog.CatalogEntry;

/**
 * 정기결제 서비스 카탈로그 한 건(GET /subscriptions/services).
 *
 * @param typicalMonthlyAmount 대표 요금제 월 금액(원). 후보 탐지·등록 화면의 기본값 용도이며 실제 청구 금액은 아니다
 * @param iconKey              FE `assets/icons/payments` 아이콘 이름. 없으면 null
 */
public record SubscriptionServiceResponse(
        String serviceCode,
        String name,
        String category,
        String categoryLabel,
        long typicalMonthlyAmount,
        String iconKey
) {

    public static SubscriptionServiceResponse from(CatalogEntry entry) {
        return new SubscriptionServiceResponse(
                entry.serviceCode(),
                entry.name(),
                entry.category().name(),
                entry.category().label(),
                entry.typicalMonthlyAmount(),
                entry.iconKey()
        );
    }
}
