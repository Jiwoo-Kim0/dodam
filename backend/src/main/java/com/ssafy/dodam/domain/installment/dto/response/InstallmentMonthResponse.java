package com.ssafy.dodam.domain.installment.dto.response;

public record InstallmentMonthResponse(String yearMonth, long scheduledAmount, long installmentCount) {
}
