package com.ssafy.dodam.domain.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * POST /api/v1/transactions — 수동 거래 등록.
 *
 * @param transactionType INCOME / EXPENSE / TRANSFER (대소문자 무시)
 * @param amount          1 이상
 * @param paymentMethod   결제수단 코드(영숫자·밑줄, 예: CASH, CARD, ACCOUNT). 대문자로 저장한다
 * @param memo            {@code TransactionMemoPolicy} 규칙(500자, 금칙문자)
 */
public record CreateManualTransactionRequest(
        @NotNull(message = "거래일은 필수입니다.")
        LocalDate transactionDate,
        @NotBlank(message = "거래 유형은 필수입니다.")
        @Size(max = 30)
        String transactionType,
        @NotNull(message = "금액은 필수입니다.")
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
}
