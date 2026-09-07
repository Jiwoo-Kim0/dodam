package com.ssafy.dodam.domain.product.model;

import java.util.List;

public record MonthlySpendingProfile(
        long totalSpending,
        List<SpendingSegment> segments
) {

    public MonthlySpendingProfile {
        segments = List.copyOf(segments);
    }

    public static MonthlySpendingProfile from(List<SpendingSegment> segments) {
        long totalSpending = segments.stream().mapToLong(SpendingSegment::amount).sum();
        return new MonthlySpendingProfile(totalSpending, segments);
    }
}
