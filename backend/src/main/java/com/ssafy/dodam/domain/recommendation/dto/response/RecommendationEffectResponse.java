package com.ssafy.dodam.domain.recommendation.dto.response;

import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 예상 개선효과(GET /{id}/effect). 8개 효과 컬럼과 계산 가정.
 *
 * @param available   효과 값이 하나라도 있으면 true. 모두 null이면 생성기가 효과를 계산하지 않은 것이다
 * @param measuredAt  효과를 추정한 시각(= 생성 시각). 실제 측정값이 아니다
 * @param assumptions 값에 적용된 가정 코드. {@code ANNUAL_IS_MONTHLY_TIMES_12}: 연간 절감 = 월 절감 × 12
 */
public record RecommendationEffectResponse(
        boolean available,
        Long monthlyCostSaving,
        Long annualCostSaving,
        Long interestSaving,
        String riskReduction,
        LocalDate currentForecastDate,
        LocalDate improvedForecastDate,
        Integer advancedDays,
        Long contributionAmount,
        LocalDateTime measuredAt,
        List<String> assumptions
) {
    public static final String ANNUAL_IS_MONTHLY_TIMES_12 = "ANNUAL_IS_MONTHLY_TIMES_12";

    public static RecommendationEffectResponse from(Recommendation r) {
        boolean available = r.getMonthlyCostSaving() != null || r.getAnnualCostSaving() != null || r.getInterestSaving() != null
                || r.getRiskReduction() != null || r.getCurrentForecastDate() != null || r.getImprovedForecastDate() != null
                || r.getAdvancedDays() != null || r.getContributionAmount() != null;
        List<String> assumptions = new ArrayList<>();
        if (r.getAnnualCostSaving() != null) {
            assumptions.add(ANNUAL_IS_MONTHLY_TIMES_12);
        }
        return new RecommendationEffectResponse(available, r.getMonthlyCostSaving(), r.getAnnualCostSaving(), r.getInterestSaving(),
                r.getRiskReduction(), r.getCurrentForecastDate(), r.getImprovedForecastDate(), r.getAdvancedDays(),
                r.getContributionAmount(), r.getCreatedAt(), assumptions);
    }
}
