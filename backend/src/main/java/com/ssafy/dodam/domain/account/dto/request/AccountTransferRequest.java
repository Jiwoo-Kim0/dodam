package com.ssafy.dodam.domain.account.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccountTransferRequest(@NotNull @Positive Long withdrawalAccountId,
        @NotNull @Positive Long depositAccountId, @Positive long amount) {
}
