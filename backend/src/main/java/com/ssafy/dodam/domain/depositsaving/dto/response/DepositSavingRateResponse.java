package com.ssafy.dodam.domain.depositsaving.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DepositSavingRateResponse(
        YearMonth yearMonth, LocalDate asOfDate, LocalDate periodStart, LocalDate periodEnd,
        Long paidAmount, Long monthlyIncome, BigDecimal rate, String reason,
        String calculationBasis, String incomeBasis
) {
}
