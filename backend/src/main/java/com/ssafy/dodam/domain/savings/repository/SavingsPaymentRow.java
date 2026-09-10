package com.ssafy.dodam.domain.savings.repository;

import java.time.LocalDate;

public record SavingsPaymentRow(
        Long paymentId, Long accountId, LocalDate paymentDate, long amount,
        int installmentNumber, String status
) {
}

