package com.ssafy.dodam.domain.cashflow.dto;

import java.time.LocalDate;
import java.util.Objects;

/** 539의 계산 입력 계약. forecastId와 저장 엔티티는 538·540에서 연결한다. */
public record CashflowPlannedEvent(
        LocalDate eventDate, String type, String name, Direction direction, long amount,
        String sourceType, String sourceId, String dedupeKey
) {
    public enum Direction { INFLOW, OUTFLOW }

    public CashflowPlannedEvent {
        Objects.requireNonNull(eventDate);
        Objects.requireNonNull(direction);
        if (amount <= 0 || !valid(type, 50) || !valid(name, 255)
                || !valid(sourceType, 30) || !valid(sourceId, 100)
                || dedupeKey == null || !dedupeKey.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("예정 이벤트 계약이 올바르지 않습니다.");
        }
    }

    private static boolean valid(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }
}
