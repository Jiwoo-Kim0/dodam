package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceSync;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncStatus;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 동기화 실행 1건. `results`는 실행 직후 응답에만 채워지고, 이력 조회에서는 null이다 (scope별 결과는 저장하지 않음).
 */
public record FinanceSyncResponse(
        Long syncId,
        FinanceSyncScope scope,
        FinanceSyncStatus status,
        Integer progress,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<FinanceSyncScopeResultResponse> results
) {

    public static FinanceSyncResponse of(FinanceSync sync, List<FinanceSyncScopeResult> results) {
        return new FinanceSyncResponse(
                sync.getId(),
                sync.getScope(),
                sync.getStatus(),
                sync.getProgress(),
                sync.getErrorMessage(),
                sync.getStartedAt(),
                sync.getCompletedAt(),
                results == null ? null : results.stream().map(FinanceSyncScopeResultResponse::from).toList()
        );
    }
}
