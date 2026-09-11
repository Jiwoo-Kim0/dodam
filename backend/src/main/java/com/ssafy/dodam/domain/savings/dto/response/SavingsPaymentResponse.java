package com.ssafy.dodam.domain.savings.dto.response;

import java.time.LocalDate;

/** 납입 이력 한 건. {@code status}는 SCHEDULED·PAID·FAILED·SKIPPED다. */
public record SavingsPaymentResponse(
        Long paymentId, LocalDate paymentDate, long amount, int installmentNumber, String status
) {
}
