package com.ssafy.dodam.domain.simulation.dto.request;

import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/simulations/loan/new 본문. 명세 17: {@code productId, principal, term, repaymentType?}.
 * {@code repaymentType}은 Loan enum 3종({@code EQUAL_PRINCIPAL, EQUAL_PAYMENT, BULLET})이며 다른 문자열은 400이다.
 * 생략하면 상품 옵션으로 한 방식이 확정될 때만 자동 선택한다.
 */
public record LoanNewSimulationRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long principal,
        @NotNull @Min(1) @Max(360) Integer term,
        RepaymentType repaymentType
) {
}
