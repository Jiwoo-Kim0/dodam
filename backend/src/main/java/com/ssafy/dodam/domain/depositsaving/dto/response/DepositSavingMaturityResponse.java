package com.ssafy.dodam.domain.depositsaving.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DepositSavingMaturityResponse(
        @JsonUnwrapped DepositSavingResponse account,
        LocalDate asOfDate,
        long daysUntilMaturity
) {
}
