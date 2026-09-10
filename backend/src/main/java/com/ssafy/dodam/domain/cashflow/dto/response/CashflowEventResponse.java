package com.ssafy.dodam.domain.cashflow.dto.response;

import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import java.time.LocalDate;

/**
 * 예정 금융이벤트 1건.
 *
 * <p>`amount`는 방향과 무관하게 양수이며 입·출금은 `direction`으로만 구분한다(538). `dedupeKey`는
 * 중복 차단용 내부 해시라 응답에 넣지 않는다.
 */
public record CashflowEventResponse(
        Long eventId,
        LocalDate eventDate,
        String type,
        String name,
        EventDirection direction,
        Long amount,
        String sourceType,
        String sourceId
) {

    public static CashflowEventResponse from(CashflowEvent event) {
        return new CashflowEventResponse(
                event.getEventId(),
                event.getEventDate(),
                event.getType(),
                event.getName(),
                event.getDirection(),
                event.getAmount(),
                event.getSourceType(),
                event.getSourceId()
        );
    }
}
