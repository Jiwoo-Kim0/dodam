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
 * GOAL/SPENDING_GOAL_RISK → SPENDING/REDUCE_SPENDING. 기획안 5-3 "목표 소비 비율 초과": 예산 대비 초과분만큼 소비 감축을 권한다.
 *
 * <p>초과분 {@code over = max(0, max(spent, projectedMonthEnd) − monthlyBudget)}은 546 goal-impact의 {@code overBudgetAmount}와 같은 식이다.
 * {@code monthlyCostSaving = over}, {@code annualCostSaving = over × 12} — 월 예산 초과는 매월 반복되는 비용이라는 계약이며
 * 이 생성기에서만 연간값을 정의한다. 초과가 0이면 후보를 만들지 않는다.
 */
@Component
@Order(20)
public class SpendingGoalRiskGenerator implements RecommendationCandidateGenerator {
    public static final String VERSION = "548.1";
    public static final String DOMAIN = "SPENDING";
    public static final String TYPE = "REDUCE_SPENDING";
    static final String REASON_OVER_BUDGET = "SPENDING_OVER_BUDGET";
    static final int MONTHS_PER_YEAR = 12;
    private final ObjectMapper json;

    public SpendingGoalRiskGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return "GOAL".equals(issue.domain()) && "SPENDING_GOAL_RISK".equals(issue.type());
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode metrics = issue.currentValue();
        Long budget = GeneratorSupport.longOf(metrics, "monthlyBudget");
        Long spent = GeneratorSupport.longOf(metrics, "spent");
        Long projected = GeneratorSupport.longOf(metrics, "projectedMonthEnd");
        Long goalId = GeneratorSupport.longOf(issue.evidence(), "targetId");
        if (budget == null || spent == null || projected == null || goalId == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        long over = Math.max(0L, Math.max(spent, projected) - budget);
        if (over == 0) {
            return Outcome.of();
        }
        String goalName = GeneratorSupport.textOf(issue.evidence().get("goalSnapshot"), "name");
        ObjectNode evidence = json.createObjectNode();
        evidence.put("monthlyBudget", budget).put("spent", spent).put("projectedMonthEnd", projected).put("overBudgetAmount", over);
        Recommendation.Effect effect = new Recommendation.Effect(over, over * MONTHS_PER_YEAR, null, null, null, null, null, null);
        String label = goalName == null ? "소비 목표" : "'" + goalName + "'";
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + goalId, issue.issueId(), DOMAIN, TYPE,
                "월 소비 " + GeneratorSupport.won(over) + " 줄이기",
                label + " 예산은 월 " + GeneratorSupport.won(budget) + "인데 이번 달 예상 지출은 "
                        + GeneratorSupport.won(Math.max(spent, projected)) + "입니다. 월 " + GeneratorSupport.won(over) + "을 줄이면 예산을 지킬 수 있습니다.",
                List.of(new RecommendationCandidate.Reason(REASON_OVER_BUDGET,
                        "예산 " + GeneratorSupport.won(budget) + " 대비 " + GeneratorSupport.won(over) + " 초과가 예상됩니다.", evidence)),
                effect));
    }
}
