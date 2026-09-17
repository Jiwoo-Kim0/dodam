package com.ssafy.dodam.domain.simulation.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/simulations/loan/refinance 본문. 명세 17: {@code accountNo, targetProductId, refinanceAmount?}.
 * {@code loanId}(2026-09-16)는 대출 계좌번호 원문 대신 내부 식별자로 본인 대출을 지정한다 — FE는 마스킹 번호만 가지므로
 * 추천 프리셋이 실행 가능한 본문을 만들려면 이 경로가 필요하다. {@code accountNo}와 {@code loanId} 중 정확히 하나를 보내야 하며
 * 둘 다 있거나 둘 다 없으면 400이다. {@code refinanceAmount}를 생략하면 기준일 잔액 전액을 대환한다.
 */
public record LoanRefinanceSimulationRequest(
        @Pattern(regexp = "^[0-9]{10,20}$") String accountNo,
        @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Long loanId,
        @NotNull @Positive Long targetProductId,
        @Positive Long refinanceAmount
) {
}
