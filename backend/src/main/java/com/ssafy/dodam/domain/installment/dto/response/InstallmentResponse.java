package com.ssafy.dodam.domain.installment.dto.response;

import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import com.ssafy.dodam.domain.installment.repository.InstallmentRow;
import java.time.LocalDate;

public record InstallmentResponse(
        Long installmentId, Long cardId, String merchantName, long totalAmount, int months,
        Long remainingPrincipal, int remainingMonths, long monthlyAmount,
        LocalDate nextPaymentDate, InstallmentStatus status
) {
    public static InstallmentResponse from(InstallmentRow row) {
        return new InstallmentResponse(
                row.installmentId(), row.cardId(), row.merchantName(), row.totalAmount(),
                row.months(), row.remainingPrincipal(), row.remainingMonths(), row.monthlyAmount(),
                row.nextPaymentDate(), row.status());
    }
}
