package com.ssafy.dodam.domain.simulation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/simulations/spending 본문. 명세 17: {@code category?, monthlyReduction, durationMonths?}.
 * {@code category}는 거래 카테고리 코드(대소문자 무시)이며 다른 Spending API와 같은 해석이다.
 */
public record SpendingSimulationRequest(
        @Size(max = 50) String category,
        @NotNull @Positive Long monthlyReduction,
        @Min(1) @Max(120) Integer durationMonths
) {
}
