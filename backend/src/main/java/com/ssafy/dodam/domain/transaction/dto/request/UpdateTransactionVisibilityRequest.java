package com.ssafy.dodam.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * PATCH /api/v1/transactions/{transactionId}/visibility
 */
public record UpdateTransactionVisibilityRequest(
        @NotNull(message = "숨김 여부는 필수입니다.")
        Boolean hidden
) {
}
