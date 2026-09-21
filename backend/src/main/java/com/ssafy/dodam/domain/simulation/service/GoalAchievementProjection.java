package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.goal.service.GoalForecastFormula;
import java.time.LocalDate;

/**
 * 모으기 목표 예상 달성일. {@code GoalCalculationService.getForecast}와 같은 식이어야 하며, 658부터 둘 다
 * {@link GoalForecastFormula}를 호출한다(예전에는 546·550 병합 충돌을 피하려고 식을 복제했다). 동일성은
 * {@code SimulationApiTest.baselineForecastMatchesGoalForecastApi}가 Goal API 응답과 대조해 고정한다.
 */
public final class GoalAchievementProjection {
    private GoalAchievementProjection() {
    }

    /** 남은 금액이 없으면 기준일, 속도가 0 이하면 null, 그 외 기준일 축 위의 일 단위 날짜. */
    public static LocalDate expectedDate(long remaining, long monthlyPace, LocalDate base) {
        return GoalForecastFormula.expectedDate(remaining, monthlyPace, base);
    }

    static long ceilDivide(long amount, long divisor) {
        return (amount + divisor - 1) / divisor;
    }
}
