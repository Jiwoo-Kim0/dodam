package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import java.time.LocalDateTime;

/**
 * 금융망 연결 상태. userKey·이메일 원문은 절대 포함하지 않는다.
 */
public record FinanceConnectionResponse(
        FinanceConnectionStatus status,
        String memberEmailMasked,
        LocalDateTime connectedAt,
        LocalDateTime disconnectedAt,
        LocalDateTime lastSyncedAt
) {

    public static FinanceConnectionResponse from(FinanceConnection connection) {
        return new FinanceConnectionResponse(
                connection.getStatus(),
                connection.getMemberEmailMasked(),
                connection.getConnectedAt(),
                connection.getDisconnectedAt(),
                connection.getLastSyncedAt()
        );
    }

    /**
     * 연결 행이 아직 없는 사용자.
     */
    public static FinanceConnectionResponse pending() {
        return new FinanceConnectionResponse(FinanceConnectionStatus.PENDING, null, null, null, null);
    }
}
