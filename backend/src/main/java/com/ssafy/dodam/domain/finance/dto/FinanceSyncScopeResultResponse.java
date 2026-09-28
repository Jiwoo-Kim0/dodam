package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeResult;

public record FinanceSyncScopeResultResponse(
        FinanceSyncScope scope,
        FinanceSyncScopeResult.Status status,
        int syncedCount,
        String message
) {

    public static FinanceSyncScopeResultResponse from(FinanceSyncScopeResult result) {
        return new FinanceSyncScopeResultResponse(
                result.scope(), result.status(), result.syncedCount(), result.message()
        );
    }
}
