package com.ssafy.dodam.domain.report.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 월간 개선행동 요약(API 20 #8). API 19의 개선 행동을 리포트 화면에 필요한 필드만 줄여 담은 것이며,
 * 상세는 API 19 #3으로 간다.
 *
 * <p>월 배정 기준은 항목마다 다르다(API 19 월별 추이와 같은 규칙).
 * <ul>
 *   <li>{@code completedActions} / {@code startedActions} / {@code canceledActions} — 그 달에 각 시각
 *       ({@code completedAt}/{@code startedAt}/{@code canceledAt})이 찍힌 행동 수</li>
 *   <li>{@code monthlySaving} — 그 달에 <b>측정된</b> 행동의 실측 월 절감액 합. 예상치는 섞지 않는다</li>
 *   <li>{@code actions} — 그 달에 위 중 하나라도 해당하는 행동. {@code actualMonthlySaving}은 측정 전이면 null</li>
 * </ul>
 */
public record MonthlyImprovementsResponse(
        String yearMonth,
        long completedActions,
        long startedActions,
        long canceledActions,
        long monthlySaving,
        List<Action> actions
) {

    public MonthlyImprovementsResponse {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record Action(
            Long actionId,
            String title,
            String status,
            LocalDateTime completedAt,
            Long actualMonthlySaving
    ) {
    }
}
