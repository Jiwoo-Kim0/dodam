package com.ssafy.dodam.domain.product.repository;

import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import java.time.YearMonth;

public interface CardRecommendationSpendingReader {

    MonthlySpendingProfile read(Long userId, YearMonth yearMonth);
}
