package com.ssafy.dodam.domain.card.dto.response;

import java.time.LocalDateTime;

public record CardPaymentSettingsResponse(
        String cardNoMasked,
        Integer withdrawalDay,
        CardWithdrawalAccountResponse withdrawalAccount,
        LocalDateTime updatedAt
) {
}
