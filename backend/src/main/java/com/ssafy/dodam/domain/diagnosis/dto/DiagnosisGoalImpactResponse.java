package com.ssafy.dodam.domain.diagnosis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 목표 영향(GET /diagnoses/{diagnosisId}/goal-impact). 기획안 6-2 「목표 달성 영향 분석」.
 *
 * <p>모든 값은 진단 시점 스냅샷에서 읽는다. 조회 시점의 목표·잔액으로 재계산하지 않는다.
 * SAVING 목표는 저축 관련 필드, SPENDING 목표는 소비 관련 필드만 채워지고 나머지는 null이다.
 *
 * @param asOf         진단 접수 시각(스냅샷 기준시각)
 * @param unavailable  기획안 항목 중 계산 근거가 없어 제공하지 못한 것과 그 사유
 */
public record DiagnosisGoalImpactResponse(Long diagnosisId, Long goalId, LocalDateTime asOf, String ruleVersion,
        List<GoalImpact> goals, List<Unavailable> unavailable, JsonNode assumptions) {

    /**
     * @param remainingAmount         목표 대비 부족 금액 = 목표 금액 − 확보 금액
     * @param monthlyShortfall        목표 대비 부족 저축액 = max(0, 필요 월 저축액 − 월평균 저축액)
     * @param expectedAchievementDate 현재 저축속도 유지 시 예상 달성일. 속도 0이면 null
     * @param daysDifference          예상 달성일 − 목표일(양수면 지연). 예상일이 없으면 null
     * @param overBudgetAmount        소비 초과 금액 = max(0, max(누적 소비, 예상 월말 소비) − 월 예산)
     * @param relatedIssues           이 목표를 대상으로 탐지된 GOAL 문제
     * @param coverage                스냅샷 형식 한계로 채우지 못한 항목 사유(예: SNAPSHOT_WITHOUT_FORECAST)
     */
    public record GoalImpact(Long goalId, String name, String type, String status, String riskLevel, List<String> factors,
            Long targetAmount, Long currentAmount, Long remainingAmount,
            Long requiredMonthlySaving, Long averageMonthlySaving, Long monthlyShortfall,
            LocalDate targetDate, LocalDate expectedAchievementDate, Long daysDifference, Boolean onSchedule,
            Long monthlyBudget, Long spent, Long projectedMonthEnd, Long overBudgetAmount,
            List<RelatedIssue> relatedIssues, List<String> coverage) { }

    public record RelatedIssue(Long issueId, String domain, String type, IssueSeverity severity) { }

    public record Unavailable(String item, String reason) { }
}
