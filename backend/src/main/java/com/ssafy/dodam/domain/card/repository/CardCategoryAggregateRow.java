package com.ssafy.dodam.domain.card.repository;

public record CardCategoryAggregateRow(
        String categoryCode,
        String categoryName,
        long amount,
        long transactionCount
) {
}
