package com.ssafy.dodam.domain.goal.dto.response;

import java.time.LocalDate;

/**
 * 목표 진행률(GET /goals/{goalId}/progress). 모으기 목표 전용.
 *
 * @param currentAmount               연결 계좌 잔액 합(조회 시점에 다시 계산해 `goal.current_amount`도 갱신)
 * @param progressRatePercent         currentAmount / targetAmount (%, 100 상한)
 * @param expectedProgressRatePercent 기간 기준 기대 진행률: 경과 일수 / 전체 일수 (%, 0~100). 목표를 만든 날부터 목표일까지를 100으로 본다
 * @param gapPercentPoint             progressRatePercent − expectedProgressRatePercent. 음수면 일정보다 뒤처짐
 * @param paceStatus                  AHEAD(+5pt 이상) / ON_TRACK(−5~+5pt) / BEHIND(−5pt 미만) / ACHIEVED(모인 금액 ≥ 목표)
 * @param linkedAccountCount          연결 계좌 수. 0이면 진행률이 움직일 수 없다
 */
public record GoalProgressResponse(
        Long goalId,
        String status,
        long targetAmount,
        long currentAmount,
        long remainingAmount,
        double progressRatePercent,
        LocalDate startDate,
        LocalDate targetDate,
        LocalDate asOfDate,
        long totalDays,
        long daysElapsed,
        long daysRemaining,
        double expectedProgressRatePercent,
        double gapPercentPoint,
        String paceStatus,
        int linkedAccountCount
) {
}
