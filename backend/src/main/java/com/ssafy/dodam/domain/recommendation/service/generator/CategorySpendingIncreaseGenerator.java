package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SPENDING/CATEGORY_SPENDING_INCREASE → SPENDING/REDUCE_CATEGORY_SPENDING(S15P21D105-627). 늘어난 카테고리 소비를 이전 기간
 * 평균 수준으로 되돌리라는 권고다.
 *
 * <p>입력은 진단이 저장한 {@code currentValue}(categoryId·categoryCode·categoryName·monthlyAverage·increaseAmount)와
 * {@code benchmarkValue.previousMonthlyAverage}다. {@code monthlyCostSaving = increaseAmount}(최근 월평균 − 이전 월평균),
 * {@code annualCostSaving = monthly × 12} — 소비 증가는 매월 반복되는 비용이라는 계약이며 {@code SpendingGoalRiskGenerator}와 같다.
 * 증가액이 0 이하면 후보를 만들지 않는다(진단 임계값상 나오지 않지만 방어한다). 후보 키는 카테고리 id라 같은 진단에서 멱등이다.
 *
 * <p>근거 {@code CATEGORY_SPENDING_INCREASE}의 evidence에 {@code categoryCode}를 남긴다. 시뮬레이션 프리셋이 카테고리를 지정한
 * 소비 감소 요청을 만들 때 이 값을 읽는다.
 */
@Component
@Order(40)
public class CategorySpendingIncreaseGenerator implements RecommendationCandidateGenerator {
    public static final String VERSION = "627.2";
    public static final String DOMAIN = "SPENDING";
    public static final String TYPE = "REDUCE_CATEGORY_SPENDING";
    public static final String REASON_INCREASE = "CATEGORY_SPENDING_INCREASE";
    public static final String CATEGORY_CODE_FIELD = "categoryCode";
    static final int MONTHS_PER_YEAR = 12;
    private final ObjectMapper json;

    public CategorySpendingIncreaseGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return "SPENDING".equals(issue.domain()) && "CATEGORY_SPENDING_INCREASE".equals(issue.type());
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode current = issue.currentValue();
        Long categoryId = GeneratorSupport.longOf(current, "categoryId");
        String categoryCode = GeneratorSupport.textOf(current, CATEGORY_CODE_FIELD);
        Long monthlyAverage = GeneratorSupport.longOf(current, "monthlyAverage");
        Long increase = GeneratorSupport.longOf(current, "increaseAmount");
        Long previousAverage = GeneratorSupport.longOf(issue.benchmarkValue(), "previousMonthlyAverage");
        if (categoryId == null || categoryCode == null || monthlyAverage == null || increase == null || previousAverage == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        if (increase <= 0) {
            return Outcome.of();
        }
        String categoryName = GeneratorSupport.textOf(current, "categoryName");
        String label = categoryName == null ? categoryCode : categoryName;
        ObjectNode evidence = json.createObjectNode();
        evidence.put("categoryId", categoryId).put(CATEGORY_CODE_FIELD, categoryCode).put("monthlyAverage", monthlyAverage)
                .put("previousMonthlyAverage", previousAverage).put("increaseAmount", increase);
        JsonNode rate = current.get("increaseRatePercent");
        if (rate != null && rate.isNumber()) {
            evidence.put("increaseRatePercent", rate.doubleValue());
        }
        JsonNode consecutive = current.get("consecutiveMonths");
        if (consecutive != null && consecutive.isNumber()) {
            evidence.put("consecutiveMonths", consecutive.intValue());
        }
        Recommendation.Effect effect = new Recommendation.Effect(increase, increase * MONTHS_PER_YEAR, null, null, null, null, null, null);
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + categoryId, issue.issueId(), DOMAIN, TYPE,
                label + " 지출 줄여보기",
                "이전 수준으로 되돌리면 월 " + GeneratorSupport.won(increase) + "을 아낄 수 있어요.",
                List.of(new RecommendationCandidate.Reason(REASON_INCREASE,
                        "이전 " + label + " 월평균 " + GeneratorSupport.won(previousAverage) + " 대비 " + GeneratorSupport.won(increase)
                                + " 증가했어요.", evidence)),
                effect));
    }
}
