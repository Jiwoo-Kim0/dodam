package com.ssafy.dodam.domain.cashflow.dto;

import java.util.List;

/** reasons가 있으면 누락 가능성이 있으므로 전체 예측을 확정 금액으로 표시하면 안 된다. */
public record CashflowEventCollection(List<CashflowPlannedEvent> events, List<String> reasons) {
    public CashflowEventCollection {
        events = List.copyOf(events);
        reasons = reasons.stream().distinct().sorted().toList();
    }

    public boolean complete() {
        return reasons.isEmpty();
    }
}
