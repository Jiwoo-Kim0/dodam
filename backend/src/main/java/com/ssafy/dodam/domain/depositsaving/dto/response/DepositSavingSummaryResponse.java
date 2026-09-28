package com.ssafy.dodam.domain.depositsaving.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DepositSavingSummaryResponse(
        long totalCount, Long totalBalance,
        long depositCount, Long depositBalance,
        long savingsCount, Long savingsBalance,
        String reason
) {
}
