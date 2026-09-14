package com.ssafy.dodam.domain.subscription.dto.request;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/subscriptions/{subscriptionId}/status — 정지/재개. 명세의 `status|action` 둘 중 하나로 보낸다.
 *
 * <ul>
 *   <li>`status`: ACTIVE(재개) 또는 PAUSED(정지)</li>
 *   <li>`action`: resume 또는 pause (대소문자 무시)</li>
 * </ul>
 * 둘 다 없거나, 둘 다 있는데 서로 다른 상태를 가리키면 400.
 */
public record ChangeSubscriptionStatusRequest(
        @Size(max = 30)
        String status,
        @Size(max = 30)
        String action
) {
}
