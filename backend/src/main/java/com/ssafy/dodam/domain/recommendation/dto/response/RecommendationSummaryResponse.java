package com.ssafy.dodam.domain.recommendation.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 개선안 요약(GET /summary).
 *
 * @param byStatus                   5개 상태 모두 포함(0 포함)
 * @param byDomain                   도메인별 건수. 추천이 없으면 빈 객체
 * @param expectedMonthlyCostSaving  PENDING·ACCEPTED 추천의 monthlyCostSaving 합(null 제외)
 * @param expectedContributionAmount PENDING·ACCEPTED 추천의 contributionAmount 합(null 제외)
 * @param discovery                  발견 화면의 통합 수치(2026-09-16). 최신 완료 진단이 없으면 null
 */
public record RecommendationSummaryResponse(
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byDomain,
        long expectedMonthlyCostSaving,
        long expectedContributionAmount,
        Discovery discovery
) {
    /**
     * 최신 완료 진단 하나를 기준으로 한 "전체 개선 시" 수치. 위의 사용자 전체 합계와 달리 진단 한 건에 묶인 열린
     * 추천(PENDING·ACCEPTED)만 보며, 같은 문제({@code sourceIssueId})의 대안 추천은 절감액이 큰 하나만 센다.
     *
     * @param issueCount              발견 화면이 세는 문제 수(675) — 진단 문제 중 GOAL 도메인을 뺀 수. {@code DiagnosisResponse.discoveryIssueCount}와 같다
     * @param openRecommendationCount 계산에 넣은 열린 추천 수(중복 제거 전, GOAL 문제에서 나온 추천 제외)
     * @param expectedAnnualCostSaving 문제별 최대 연 절감액의 합. 금액 있는 추천이 없으면 null
     * @param expectedGoalAdvanceDays 열린 추천의 {@code advancedDays} 최댓값. 값이 있는 추천이 없으면 null
     * @param notes                   계산 가정·불가 사유 코드
     */
    public record Discovery(
            Long diagnosisId,
            int issueCount,
            int openRecommendationCount,
            Long expectedAnnualCostSaving,
            Integer expectedGoalAdvanceDays,
            List<String> notes
    ) {
        /** 연 절감액은 문제별 최대 대안 하나만 더했다. */
        public static final String ANNUAL_SAVING_BEST_ALTERNATIVE_PER_ISSUE = "ANNUAL_SAVING_BEST_ALTERNATIVE_PER_ISSUE";
        /** 목표 단축일은 추천별 값의 최댓값이며 복합 시뮬레이션으로 합산한 값이 아니다. */
        public static final String GOAL_ADVANCE_IS_MAX_NOT_COMBINED = "GOAL_ADVANCE_IS_MAX_NOT_COMBINED";
        public static final String NO_OPEN_RECOMMENDATION = "NO_OPEN_RECOMMENDATION";
        public static final String ANNUAL_SAVING_UNAVAILABLE = "ANNUAL_SAVING_UNAVAILABLE";
        public static final String GOAL_ADVANCE_UNAVAILABLE = "GOAL_ADVANCE_UNAVAILABLE";
        /** (675) 목표 위험(GOAL) 문제와 그 문제에서 나온 추천은 발견 목록에 없으므로 문제 수·절감액·단축일에서 뺐다. */
        public static final String GOAL_ISSUES_EXCLUDED = "GOAL_ISSUES_EXCLUDED";
        public static final String EXCLUDED_ISSUE_DOMAIN = "GOAL";

        public Discovery {
            notes = List.copyOf(notes);
        }
    }
}
