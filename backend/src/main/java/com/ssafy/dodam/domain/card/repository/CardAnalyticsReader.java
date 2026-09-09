package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface CardAnalyticsReader {

    CardMonthlyAggregateRow readMonthly(Long userId, Long cardId, YearMonth yearMonth);

    List<CardCategoryAggregateRow> readCategories(
            Long userId,
            Long cardId,
            YearMonth yearMonth
    );

    MonthlySpendingProfile readSpendingProfile(
            Long userId,
            Long cardId,
            YearMonth yearMonth
    );

    List<LowUsageCardRow> readLowUsageCards(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            long thresholdAmount
    );
}
