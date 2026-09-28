package com.ssafy.dodam.domain.minusaccount.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 마이너스통장 원금 상환 요청.
 *
 * @param amount         상환할 원금(원). 사용액을 초과할 수 없다.
 * @param repayAccountNo 출금계좌 번호. 생략하면 본인의 활성 입출금계좌가 정확히 1개일 때만
 *                       그 계좌를 쓰고, 0개거나 2개 이상이면 지정을 요구한다.
 */
public record MinusAccountRepaymentRequest(
        @NotNull @Positive Long amount,
        @Pattern(regexp = "^[0-9]{10,20}$") String repayAccountNo
) {
}
