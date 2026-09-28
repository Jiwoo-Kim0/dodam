package com.ssafy.dodam.domain.finance.dto;

/**
 * @param inProgress 진행 중인 동기화가 있는지 (만료된 RUNNING 행은 진행 중으로 보지 않음)
 * @param sync       가장 최근 동기화. 이력이 없으면 null
 */
public record FinanceSyncStatusResponse(
        boolean inProgress,
        FinanceSyncResponse sync
) {
}
