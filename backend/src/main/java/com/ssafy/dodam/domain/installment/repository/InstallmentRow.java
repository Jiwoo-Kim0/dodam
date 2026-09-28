package com.ssafy.dodam.domain.installment.repository;

import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentRow(
        Long installmentId,
        Long cardId,
        String merchantName,
        long totalAmount,
        int months,
        Long remainingPrincipal,
        int remainingMonths,
        long monthlyAmount,
        BigDecimal feeRate,
        LocalDate purchaseDate,
        LocalDate firstPaymentDate,
        LocalDate nextPaymentDate,
        InstallmentStatus status
) {
}
