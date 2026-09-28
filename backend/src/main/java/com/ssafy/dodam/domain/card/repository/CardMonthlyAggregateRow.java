package com.ssafy.dodam.domain.card.repository;

public record CardMonthlyAggregateRow(
        long totalAmount,
        long transactionCount
) {
}
