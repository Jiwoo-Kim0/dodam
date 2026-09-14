package com.ssafy.dodam.domain.simulation.service;

import java.time.LocalDate;

/**
 * 모으기 목표 예상 달성일. {@code GoalCalculationService.getForecast}와 같은 식이어야 한다:
 * 남은 금액이 없으면 기준일, 속도가 0 이하면 null, 그 외 {@code base + ceil(remaining / pace)}개월.
 *
 * <p>Goal 도메인의 메서드를 호출하지 않고 여기 복제한 이유: develop의 `getForecast`는 목표 잔액을 갱신하고 이력을 남기는
 * 쓰기 경로라 시뮬레이션이 부를 수 없고, 546이 같은 메서드를 수정 중이라 550에서 Goal 코드를 건드리면 병합 충돌이 난다.
 * 동일성은 {@code GoalAchievementProjectionTest}가 Goal API 응답과 대조해 고정한다.
 */
public final class GoalAchievementProjection {
    private GoalAchievementProjection() {
    }

    public static LocalDate expectedDate(long remaining, long monthlyPace, LocalDate base) {
        if (remaining <= 0) {
            return base;
        }
        if (monthlyPace <= 0) {
            return null;
        }
        return base.plusMonths(ceilDivide(remaining, monthlyPace));
    }

    static long ceilDivide(long amount, long divisor) {
        return (amount + divisor - 1) / divisor;
    }
}
