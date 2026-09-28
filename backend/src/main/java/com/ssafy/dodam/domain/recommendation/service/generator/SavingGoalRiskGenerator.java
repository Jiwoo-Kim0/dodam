package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * GOAL/SAVING_GOAL_RISK → SAVING/INCREASE_MONTHLY_SAVING. 기획안 5-3 "저축 목표 부족": 목표 저축액 R원, 현재 평균 A원이면
 * 월 (R − A)원 추가 저축을 권한다.
 *
 * <p>입력은 진단이 저장한 값만 쓴다 — {@code currentValue}(GoalRiskResponse.Metrics)의 R·A, {@code evidence.savingGoalSnapshot.targetAmount},
 * {@code evidence.goalSnapshot.currentAmount}. 현재·개선 예상 달성일을 같은 식·같은 기준일로 계산해야 {@code advancedDays}가 일관되므로
 * 546이 스냅샷에 넣는 {@code forecast}는 읽지 않는다(v2·v3 진단에서 같은 결과).
 *
 * <p>R − A ≤ 0(속도가 이미 충분)이거나 남은 금액이 0이면 후보를 만들지 않는다. 금액 필드가 없으면 후보는 만들되 날짜 효과를 비우고
 * 근거 {@code SNAPSHOT_FIELD_MISSING}을 남긴다 — 저축 증액 권고 자체는 R·A만으로 성립한다.
 */
@Component
@Order(10)
public class SavingGoalRiskGenerator implements RecommendationCandidateGenerator {
    public static final String VERSION = "604.1";
    public static final String DOMAIN = "SAVING";
    public static final String TYPE = "INCREASE_MONTHLY_SAVING";
    public static final String REASON_SHORTFALL = "GOAL_SAVING_SHORTFALL";
    static final String REASON_FORECAST_DELAY = "GOAL_FORECAST_DELAY";
    static final String REASON_FIELD_MISSING = "SNAPSHOT_FIELD_MISSING";
    private final ObjectMapper json;

    public SavingGoalRiskGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return "GOAL".equals(issue.domain()) && "SAVING_GOAL_RISK".equals(issue.type());
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode metrics = issue.currentValue();
        Long required = GeneratorSupport.longOf(metrics, "requiredMonthlySaving");
        Long average = GeneratorSupport.longOf(metrics, "averageMonthlySaving");
        Long goalId = GeneratorSupport.longOf(issue.evidence(), "targetId");
        if (required == null || average == null || goalId == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        long shortfall = required - average;
        if (shortfall <= 0) {
            return Outcome.of();
        }
        JsonNode goalSnapshot = issue.evidence().get("goalSnapshot");
        JsonNode savingSnapshot = issue.evidence().get("savingGoalSnapshot");
        Long target = GeneratorSupport.longOf(savingSnapshot, "targetAmount");
        Long current = GeneratorSupport.longOf(goalSnapshot, "currentAmount");
        String goalName = GeneratorSupport.textOf(goalSnapshot, "name");
        LocalDate base = GeneratorSupport.baseDate(input);

        LocalDate currentDate = null;
        LocalDate improvedDate = null;
        List<RecommendationCandidate.Reason> reasons = new ArrayList<>();
        ObjectNode shortfallEvidence = json.createObjectNode();
        shortfallEvidence.put("requiredMonthlySaving", required).put("averageMonthlySaving", average).put("shortfall", shortfall);
        reasons.add(new RecommendationCandidate.Reason(REASON_SHORTFALL,
                "목표 달성에 월 " + GeneratorSupport.won(required) + "이 필요하지만 현재 평균 저축은 월 " + GeneratorSupport.won(average) + "입니다.",
                shortfallEvidence));
        if (target == null || current == null) {
            reasons.add(new RecommendationCandidate.Reason(REASON_FIELD_MISSING,
                    "목표 금액·모인 금액 자료가 없어 예상 달성일 변화는 계산하지 못했어요.", null));
        } else {
            long remaining = Math.max(0L, target - current);
            if (remaining == 0) {
                return Outcome.of();
            }
            currentDate = GeneratorSupport.expectedDate(remaining, average, base);
            improvedDate = GeneratorSupport.expectedDate(remaining, required, base);
            ObjectNode delay = json.createObjectNode();
            delay.put("baseDate", base.toString()).put("remaining", remaining)
                    .put("currentForecastDate", currentDate == null ? null : currentDate.toString())
                    .put("improvedForecastDate", improvedDate.toString());
            reasons.add(new RecommendationCandidate.Reason(REASON_FORECAST_DELAY,
                    currentDate == null ? "지금 저축 속도로는 목표에 닿기 어려워요. 월 " + GeneratorSupport.won(required)
                            + "씩 저축하면 " + improvedDate + "에 달성할 수 있어요."
                            : "지금 속도로는 " + currentDate + ", 월 " + GeneratorSupport.won(required) + "씩 저축하면 " + improvedDate
                            + "에 달성할 수 있어요.", delay));
        }

        String label = goalName == null ? "목표" : "'" + goalName + "'";
        Recommendation.Effect effect = new Recommendation.Effect(null, null, null, null, currentDate, improvedDate,
                GeneratorSupport.advancedDays(currentDate, improvedDate), shortfall);
        // 696: 목표를 지정하지 않은 진단에서도 goal-impact·저축 프리셋(619)이 이 목표를 가리키도록 후보에 goalId를 싣는다.
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + goalId, issue.issueId(), DOMAIN, TYPE,
                "저축 조금씩 늘려보기",
                "월 " + GeneratorSupport.won(shortfall) + "을 더 저축하면 " + label + " 달성에 다가갈 수 있어요.",
                reasons, effect, goalId));
    }
}
