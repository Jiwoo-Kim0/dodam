package com.ssafy.dodam.domain.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 월간 목표변화(API 20 #6). 기준 월 <b>안에서</b> 목표가 얼마나 움직였는지를 본다.
 *
 * <p>값은 {@code goal_history}에서 읽는다 — 목표 잔액·예상 달성일은 그 테이블에 기록된 시점 값이라야 과거 달을
 * 재현할 수 있다. 현재 잔액으로 과거 달을 다시 계산하면 그 달의 변화가 아니라 오늘의 값이 나온다.
 *
 * <ul>
 *   <li>{@code startAmount} — 그 달 시작 직전 마지막 기록. 기록이 없으면 null</li>
 *   <li>{@code endAmount} — 그 달 마지막 기록. 그 달에 기록이 없으면 {@code startAmount}와 같다</li>
 *   <li>{@code contributed = endAmount − startAmount}. 한쪽이 null이면 null이며 0이 아니다</li>
 *   <li>{@code advancedDays} 양수 = 달성일이 앞당겨졌다. 두 예상일이 모두 있어야 값이 생긴다</li>
 * </ul>
 *
 * <p>{@code goalId}를 주면 해당 목표 1건만 담는다. 남의 목표·삭제된 목표는 404다.
 * {@code unavailableReason}: {@code NO_HISTORY}(그 목표의 기록이 아예 없다),
 * {@code NO_HISTORY_BEFORE_PERIOD}(그 달 이전 기록이 없어 시작값을 모른다).
 */
public record MonthlyGoalImpactResponse(String yearMonth, List<Goal> goals) {

    public MonthlyGoalImpactResponse {
        goals = goals == null ? List.of() : List.copyOf(goals);
    }

    public record Goal(
            Long goalId,
            String goalName,
            Long startAmount,
            Long endAmount,
            Long contributed,
            Double progressRatio,
            LocalDate forecastDateAtStart,
            LocalDate forecastDateAtEnd,
            Long advancedDays,
            String unavailableReason
    ) {
    }
}
