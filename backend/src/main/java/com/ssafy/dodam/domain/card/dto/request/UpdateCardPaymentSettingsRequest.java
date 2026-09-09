package com.ssafy.dodam.domain.card.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCardPaymentSettingsRequest(
        @Size(max = 64)
        @Pattern(regexp = "^[0-9]+$", message = "withdrawalAccountNo는 숫자만 입력해야 합니다.")
        String withdrawalAccountNo,
        @Min(1) @Max(31) Integer withdrawalDay
) {

    public boolean hasNoFields() {
        return withdrawalAccountNo == null && withdrawalDay == null;
    }
}
