package com.ssafy.dodam.domain.product.model;

public record SpendingSegment(
        String categoryCode,
        String paymentMethod,
        long amount
) {
}
