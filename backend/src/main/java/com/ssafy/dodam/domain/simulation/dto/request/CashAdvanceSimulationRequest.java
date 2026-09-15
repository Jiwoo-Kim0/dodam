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
 * POST /api/v1/simulations/cash-advance 본문. 명세 17: {@code amount, rate, repaymentMonths?}.
 * {@code repaymentMonths}를 생략하면 1개월 뒤 만기 일시상환이다(생략·null·1은 같은 지문).
 */
public record CashAdvanceSimulationRequest(
        @NotNull @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long amount,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal rate,
        @Min(1) @Max(120) @JsonDeserialize(using = WholeNumberDeserializer.ForInteger.class) Integer repaymentMonths
) {
}
