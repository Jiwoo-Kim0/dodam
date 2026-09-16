package com.ssafy.dodam.domain.transaction.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * PUT /api/v1/transactions/{transactionId} — 수동 거래 수정. 입력된 필드만 바꾼다.
 *
 * <p>null(또는 생략)은 "변경 없음"이다. `merchantName`·`paymentMethod`·`memo`는 빈 문자열을 보내면 값을 지운다.
 * `categoryId`·`transactionTime`은 이 API로 지울 수 없다(다른 값으로 바꾸는 것만 가능).
 */
public record UpdateManualTransactionRequest(
        LocalDate transactionDate,
        LocalTime transactionTime,
        @Size(max = 30)
        String transactionType,
        @Positive(message = "금액은 1 이상이어야 합니다.")
        Long amount,
        @Size(max = 255, message = "가맹점명은 255자 이하여야 합니다.")
        String merchantName,
        @Positive(message = "카테고리 ID는 1 이상이어야 합니다.")
        Long categoryId,
        @Size(max = 30, message = "결제수단은 30자 이하여야 합니다.")
        @Pattern(regexp = "^\\s*$|^[A-Za-z0-9_]+$", message = "결제수단은 영숫자와 밑줄만 사용할 수 있습니다.")
        String paymentMethod,
        String memo
) {

    public boolean isEmpty() {
        return transactionDate == null && transactionTime == null && transactionType == null && amount == null
                && merchantName == null && categoryId == null && paymentMethod == null && memo == null;
    }
}
