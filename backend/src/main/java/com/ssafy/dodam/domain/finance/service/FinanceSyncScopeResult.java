package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;

/**
 * scope 1개의 동기화 결과. DB에는 저장하지 않고 실행 응답에만 싣는다.
 *
 * @param syncedCount 반영한 행 수 (SKIPPED·FAILED는 0)
 */
public record FinanceSyncScopeResult(
        FinanceSyncScope scope,
        Status status,
        int syncedCount,
        String message
) {

    public enum Status {
        COMPLETED,
        /** 해당 도메인의 동기화 구현이 아직 등록되지 않았다. */
        SKIPPED,
        FAILED
    }

    public static FinanceSyncScopeResult completed(FinanceSyncScope scope, int syncedCount) {
        return new FinanceSyncScopeResult(scope, Status.COMPLETED, syncedCount, null);
    }

    public static FinanceSyncScopeResult skipped(FinanceSyncScope scope) {
        return new FinanceSyncScopeResult(
                scope, Status.SKIPPED, 0, "동기화 구현이 아직 등록되지 않은 도메인입니다."
        );
    }

    public static FinanceSyncScopeResult failed(FinanceSyncScope scope, String message) {
        return new FinanceSyncScopeResult(scope, Status.FAILED, 0, message);
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
