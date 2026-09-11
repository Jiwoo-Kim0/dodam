package com.ssafy.dodam.domain.subscription.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PATCH /api/v1/subscriptions/{subscriptionId} — 정기결제 수정. null(생략)은 "변경 없음"이다. 모든 필드가 비어 있으면 400 NO_FIELDS_TO_UPDATE.
 *
 * <ul>
 *   <li>`paymentAmount` 1 이상, `paymentDay` 1~31</li>
 *   <li>`endDate`는 오늘 이후여야 하고, 다음 결제일이 있으면 그 날 이후여야 한다(DB CHECK와 같은 규칙)</li>
 *   <li>`status`는 ACTIVE / PAUSED / CANCELED. 허용된 전이만 가능하고 종료 상태(CANCELED·EXPIRED)는 어떤 필드도 바꿀 수 없다</li>
 * </ul>
 */
public record UpdateSubscriptionRequest(
        @Positive(message = "결제 금액은 1 이상이어야 합니다.")
        Long paymentAmount,
        @Min(value = 1, message = "결제일은 1~31이어야 합니다.")
        @Max(value = 31, message = "결제일은 1~31이어야 합니다.")
        Integer paymentDay,
        LocalDate endDate,
        @Size(max = 30)
        String status
) {

    public boolean isEmpty() {
        return paymentAmount == null && paymentDay == null && endDate == null && status == null;
    }
}
