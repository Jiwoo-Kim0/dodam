package com.ssafy.dodam.domain.improvement.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 목표달성일 개선(API 19 #9). 기획안 5-6 "목표 달성 예상 시점 3개월 단축" 화면.
 *
 * <p>ACTIVE 모으기 목표만 담는다. 목표가 없으면 {@code goals: []}.
 *
 * <p>{@code advancedDays} 양수 = 앞당겨짐, 음수 = 밀림. null이면 한쪽 예상일을 계산할 수 없다.
 * 사유 코드: {@code NO_CONTRIBUTING_ACTION}(그 목표에 연결된 개선 행동이 없다),
 * {@code BASELINE_FORECAST_UNAVAILABLE}(수락 시점 예상일이 없다 — 그때 저축속도가 0이었다),
 * {@code CURRENT_FORECAST_UNAVAILABLE}(지금 저축속도가 0이라 달성 시점을 낼 수 없다).
 *
 * @param baselineForecastDate 그 목표에 연결된 <b>가장 이른</b> 개선 행동의 수락 시점 예상 달성일
 * @param currentForecastDate  조회 시점의 예상 달성일
 * @param contributingActions  그 목표에 연결된 취소되지 않은 개선 행동 수
 */
public record GoalImpactSummaryResponse(List<Goal> goals) {

    public GoalImpactSummaryResponse {
        goals = goals == null ? List.of() : List.copyOf(goals);
    }

    public record Goal(
            Long goalId,
            String goalName,
            LocalDate baselineForecastDate,
            LocalDate currentForecastDate,
            Long advancedDays,
            long contributingActions,
            String unavailableReason
    ) {
    }
}
