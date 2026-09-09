package com.ssafy.dodam.domain.finance.dto;

import java.time.LocalDateTime;

/**
 * @param lastSyncedAt      마지막으로 금융 데이터가 실제 반영된 시각(finance_connection.last_synced_at). 없으면 null
 * @param lastCompletedSync 마지막으로 COMPLETED된 동기화. 없으면 null
 */
public record FinanceSyncLatestResponse(
        LocalDateTime lastSyncedAt,
        FinanceSyncResponse lastCompletedSync
) {
}
