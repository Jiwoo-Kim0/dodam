package com.ssafy.dodam.domain.installment.dto.response;

import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import com.ssafy.dodam.domain.installment.repository.InstallmentRow;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentDetailResponse(
        Long installmentId, Long cardId, String merchantName, long totalAmount, int months,
        Long remainingPrincipal, int remainingMonths, long monthlyAmount,
        LocalDate nextPaymentDate, InstallmentStatus status,
        BigDecimal feeRate, LocalDate purchaseDate, LocalDate firstPaymentDate
) {
    public static InstallmentDetailResponse from(InstallmentRow row) {
        return new InstallmentDetailResponse(
                row.installmentId(), row.cardId(), row.merchantName(), row.totalAmount(),
                row.months(), row.remainingPrincipal(), row.remainingMonths(), row.monthlyAmount(),
                row.nextPaymentDate(), row.status(), row.feeRate(),
                row.purchaseDate(), row.firstPaymentDate());
    }
}
