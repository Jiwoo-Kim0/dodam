package com.ssafy.dodam.domain.subscription.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/**
 * ERD `subscription.status` CHECK 제약과 동일한 값. 선언 순서가 목록 정렬 우선순위다(결제 중인 것이 먼저).
 *
 * <pre>
 * ACTIVE ──▶ PAUSED ──▶ ACTIVE
 *   │           │
 *   └──▶ CANCELED (종료) ◀──┘
 * EXPIRED (종료): end_date가 지난 정기결제. 사용자가 직접 지정할 수 없다.
 * </pre>
 * CANCELED·EXPIRED는 종료 상태라 어디로도 옮길 수 없다.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    EXPIRED,
    CANCELED;

    public boolean canTransitionTo(SubscriptionStatus next) {
        return switch (this) {
            case ACTIVE -> next == PAUSED || next == CANCELED;
            case PAUSED -> next == ACTIVE || next == CANCELED;
            case EXPIRED, CANCELED -> false;
        };
    }

    public boolean isTerminal() {
        return this == CANCELED || this == EXPIRED;
    }

    /**
     * @throws InvalidRequestException 정의되지 않은 값
     */
    public static SubscriptionStatus parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException("지원하지 않는 정기결제 상태입니다.");
        }
    }

    /** 목록 필터. null·빈 값은 전체. */
    public static SubscriptionStatus parseFilter(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
