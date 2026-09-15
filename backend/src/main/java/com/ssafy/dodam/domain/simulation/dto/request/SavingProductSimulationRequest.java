package com.ssafy.dodam.domain.simulation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/simulations/saving-product 본문. 명세 17: {@code productId, monthlyAmount, term}.
 * {@code monthlyAmount}는 적금이면 월 납입액, 예금이면 일시 예치 원금이다(명세에 해석이 없어 서비스가 정한 값).
 * {@code term}은 개월이며 상품 옵션의 term과 정확히 일치해야 한다.
 */
public record SavingProductSimulationRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long monthlyAmount,
        @NotNull @Min(1) @Max(120) Integer term
) {
}
