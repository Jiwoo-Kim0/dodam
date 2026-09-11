package com.ssafy.dodam.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * PATCH /api/v1/transactions/{transactionId}/category
 */
public record UpdateTransactionCategoryRequest(
        @NotNull(message = "카테고리 ID는 필수입니다.")
        @Positive(message = "카테고리 ID는 1 이상이어야 합니다.")
        Long categoryId
) {
}
