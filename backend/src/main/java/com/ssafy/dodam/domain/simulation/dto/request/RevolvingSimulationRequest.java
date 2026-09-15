package com.ssafy.dodam.domain.simulation.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * POST /api/v1/simulations/revolving 본문. 명세 17: {@code cardId?, balance, paymentRatio, rate}.
 * {@code cardId}는 본인 카드여야 하며(없거나 타인이면 404) 소유권·식별에만 쓴다. 카드번호는 받지 않는다.
 * {@code paymentRatio}는 0 초과 100 이하 %, {@code rate}는 연 %(0~100), 둘 다 소수 4자리까지다.
 */
public record RevolvingSimulationRequest(
        @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long cardId,
        @NotNull @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long balance,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal paymentRatio,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal rate
) {
}
