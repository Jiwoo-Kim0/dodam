package com.ssafy.dodam.domain.simulation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * POST /api/v1/simulations/saving 본문. 명세 17: {@code accountNo?, monthlySavingAmount, durationMonths?}.
 * {@code accountNo}는 원문으로 받되 해시로만 조회하고 저장·로그·응답에 남기지 않는다.
 * {@code monthlySavingAmount}는 변경 후 월 저축액(절대값)이다.
 */
public record SavingSimulationRequest(
        @Pattern(regexp = "^[0-9-]{8,32}$") String accountNo,
        @NotNull @PositiveOrZero Long monthlySavingAmount,
        @Min(1) @Max(120) Integer durationMonths
) {
}
