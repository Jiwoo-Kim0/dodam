package com.ssafy.dodam.domain.simulation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/simulations/loan/refinance 본문. 명세 17: {@code accountNo, targetProductId, refinanceAmount?}.
 * {@code refinanceAmount}를 생략하면 기준일 잔액 전액을 대환한다.
 */
public record LoanRefinanceSimulationRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{10,20}$") String accountNo,
        @NotNull @Positive Long targetProductId,
        @Positive Long refinanceAmount
) {
}
