package com.ssafy.dodam.domain.transaction.dto.request;

/**
 * PATCH /api/v1/transactions/{transactionId}/memo
 *
 * <p>memo가 null·빈 값·공백만이면 메모를 지운다. 길이·금칙문자 검증은 {@code TransactionMemoPolicy}가 한다.
 */
public record UpdateTransactionMemoRequest(
        String memo
) {
}
