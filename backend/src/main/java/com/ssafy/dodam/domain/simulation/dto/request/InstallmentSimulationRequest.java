package com.ssafy.dodam.domain.simulation.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * POST /api/v1/simulations/installment 본문. 명세 17: {@code purchaseAmount, months, feeRate?}.
 * {@code feeRate}는 연 %(소수 4자리까지, 0~100)이며 생략하면 0%로 계산하고 {@code ZERO_FEE_ASSUMED}를 남긴다.
 * 금액·개월 수에 소수가 오면 절사하지 않고 400이다.
 */
public record InstallmentSimulationRequest(
        @NotNull @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long purchaseAmount,
        @NotNull @Min(1) @Max(120) @JsonDeserialize(using = WholeNumberDeserializer.ForInteger.class) Integer months,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal feeRate
) {
}
