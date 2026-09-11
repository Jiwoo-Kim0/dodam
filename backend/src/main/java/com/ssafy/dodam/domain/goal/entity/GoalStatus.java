package com.ssafy.dodam.domain.goal.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * ERD `goal.status` CHECK 제약과 동일한 값. 선언 순서가 목록 정렬 우선순위다(진행 중인 목표가 먼저).
 *
 * <pre>
 * ACTIVE ──▶ PAUSED ──▶ ACTIVE
 *   │           │
 *   ├──▶ ACHIEVED (종료)
 *   └──▶ CANCELED (종료) ◀── PAUSED
 * </pre>
 * ACHIEVED·CANCELED는 종료 상태라 어디로도 옮길 수 없다.
 */
public enum GoalStatus {
    ACTIVE,
    PAUSED,
    ACHIEVED,
    CANCELED;

    private static final Set<GoalStatus> FROM_ACTIVE = EnumSet.of(PAUSED, ACHIEVED, CANCELED);
    private static final Set<GoalStatus> FROM_PAUSED = EnumSet.of(ACTIVE, CANCELED);

    public boolean canTransitionTo(GoalStatus next) {
        return switch (this) {
            case ACTIVE -> FROM_ACTIVE.contains(next);
            case PAUSED -> FROM_PAUSED.contains(next);
            case ACHIEVED, CANCELED -> false;
        };
    }

    public boolean isTerminal() {
        return this == ACHIEVED || this == CANCELED;
    }

    /**
     * @throws InvalidRequestException 정의되지 않은 값
     */
    public static GoalStatus parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException("지원하지 않는 목표 상태입니다.");
        }
    }

    /** 목록 필터. null·빈 값은 전체. */
    public static GoalStatus parseFilter(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
