package com.ssafy.dodam.domain.minusaccount.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 마이너스통장 이자 납부 요청.
 *
 * @param amount           납부할 이자(원). 생략하면 잔여 미수이자 전액을 납부한다.
 *                         명세가 이 값을 선택 입력으로 둔 것을 전액 납부 의도로 해석했다.
 * @param paymentAccountNo 출금계좌 번호. 생략 시 처리는 상환과 같다.
 */
public record MinusAccountInterestPaymentRequest(
        @Positive Long amount,
        @Pattern(regexp = "^[0-9]{10,20}$") String paymentAccountNo
) {
}
