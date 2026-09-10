package com.ssafy.dodam.domain.depositsaving.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DepositSavingResponse(
        Long accountId, String accountName, String bankCode, String accountNoMasked,
        String type, String status, Long balance, BigDecimal interestRate,
        LocalDate openingDate, LocalDate expiryDate, Long monthlyAmount,
        boolean detailsAvailable
) {
}
