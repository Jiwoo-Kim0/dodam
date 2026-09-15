package com.ssafy.dodam.domain.simulation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * POST /api/v1/simulations/loan/prepayment 본문. 명세 17: {@code accountNo, prepaymentAmount, paymentDate?}.
 * {@code accountNo}는 대출 계좌번호 원문이며 해시로만 본인 대출을 찾고 저장·로그·응답에 남기지 않는다.
 * {@code paymentDate}(ISO 날짜)를 생략하면 실행 기준일이다.
 */
public record LoanPrepaymentSimulationRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{10,20}$") String accountNo,
        @NotNull @Positive Long prepaymentAmount,
        LocalDate paymentDate
) {
}
