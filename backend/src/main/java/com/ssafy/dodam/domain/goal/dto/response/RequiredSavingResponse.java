package com.ssafy.dodam.domain.goal.dto.response;

import java.time.LocalDate;

/**
 * 필요 월 저축액(GET /goals/{goalId}/required-saving). 모으기 목표 전용. 기획안 「모으기 목표 달성 위험」의 파생 데이터.
 *
 * @param monthsRemaining         기준일부터 목표일까지 남은 개월 수(올림, 목표일이 지났으면 0)
 * @param requiredMonthlySaving   남은 금액 / 남은 개월(올림). 목표일이 지났거나 이번 달이 마지막이면 남은 금액 전체
 * @param requiredDailySaving     남은 금액 / 남은 일수(올림). 남은 일이 없으면 남은 금액 전체
 * @param monthsElapsed           목표를 만든 날부터 기준일까지 경과 개월(최소 1)
 * @param averageMonthlySaving    현재 저축속도. 연결 계좌의 최근 3개 완료 월 순증가(월 평균, {@code GoalSavingPaceReader}, 694)
 * @param monthlyShortfall        requiredMonthlySaving − averageMonthlySaving. 양수면 지금 속도로는 미달
 * @param feasibility             ACHIEVED(남은 금액 0) / SUFFICIENT(속도 ≥ 필요액) / INSUFFICIENT(속도 < 필요액) / DEADLINE_PASSED
 * @param requiredRatioOfIncomePercent 필요 월 저축액 / 월 소득(금융 기본정보). 소득 정보가 없으면 null
 */
public record RequiredSavingResponse(
        Long goalId,
        long targetAmount,
        long currentAmount,
        long remainingAmount,
        LocalDate asOfDate,
        LocalDate targetDate,
        int monthsRemaining,
        long daysRemaining,
        long requiredMonthlySaving,
        long requiredDailySaving,
        int monthsElapsed,
        long averageMonthlySaving,
        long monthlyShortfall,
        String feasibility,
        Double requiredRatioOfIncomePercent
) {
}
