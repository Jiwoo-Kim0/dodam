package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator.Outcome;
import com.ssafy.dodam.domain.recommendation.service.RecommendationPriorityPolicy;
import com.ssafy.dodam.domain.recommendation.service.generator.CashflowShortageGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SavingGoalRiskGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SpendingGoalRiskGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 생성기·우선순위를 DB 없이 검증한다. 문제 JSON은 DiagnosisEvaluator가 저장하는 형식을 손으로 만든 것이며,
 * 실제 진단 실행으로 만든 형식과의 정합은 RecommendationApiTest가 맡는다.
 */
class RecommendationGeneratorTest {
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 9, 13, 12, 0);
    private static final LocalDate BASE = AS_OF.toLocalDate();
    private final ObjectMapper json = new ObjectMapper();
    private final SavingGoalRiskGenerator saving = new SavingGoalRiskGenerator(json);
    private final SpendingGoalRiskGenerator spending = new SpendingGoalRiskGenerator(json);
    private final CashflowShortageGenerator cashflow = new CashflowShortageGenerator(json);

    private RecommendationInput input(RecommendationInput.Issue... issues) {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("asOf", AS_OF.toString());
        return new RecommendationInput(1L, 1L, "v2", 7L, AS_OF.plusHours(5), snapshot, List.of(issues));
    }

    private RecommendationInput.Issue issue(long id, String domain, String type, IssueSeverity severity, JsonNode current, JsonNode evidence) {
        return new RecommendationInput.Issue(id, domain, type, severity, "t", "d", current, null, evidence, DetectionSource.RULE, null, null);
    }

    private RecommendationInput.Issue savingIssue(long required, long average, Long target, Long current) {
        ObjectNode metrics = json.createObjectNode().put("requiredMonthlySaving", required).put("averageMonthlySaving", average);
        ObjectNode evidence = json.createObjectNode().put("targetId", 7L);
        ObjectNode goal = evidence.putObject("goalSnapshot").put("name", "비상금");
        if (current != null) {
            goal.put("currentAmount", current);
        }
        if (target != null) {
            evidence.putObject("savingGoalSnapshot").put("targetAmount", target);
        }
        return issue(11L, "GOAL", "SAVING_GOAL_RISK", IssueSeverity.HIGH, metrics, evidence);
    }

    @Test
    void savingGeneratorComputesForecastFromSnapshotWithSameFormulaForBothDates() {
        // 남은 2,000,000 / 현재 250,000 → 8개월, 필요 500,000 → 4개월: 약 122일 단축
        Outcome outcome = saving.generate(input(), savingIssue(500_000L, 250_000L, 3_000_000L, 1_000_000L));
        assertThat(outcome.skipReason()).isNull();
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.candidateKey()).isEqualTo("INCREASE_MONTHLY_SAVING:7");
        assertThat(candidate.domain()).isEqualTo("SAVING");
        assertThat(candidate.sourceIssueId()).isEqualTo(11L);
        Recommendation.Effect effect = candidate.effect();
        assertThat(effect.contributionAmount()).isEqualTo(250_000L);
        assertThat(effect.currentForecastDate()).isEqualTo(BASE.plusMonths(8));
        assertThat(effect.improvedForecastDate()).isEqualTo(BASE.plusMonths(4));
        assertThat(effect.advancedDays()).isEqualTo((int) java.time.temporal.ChronoUnit.DAYS.between(BASE.plusMonths(4), BASE.plusMonths(8)));
        assertThat(effect.monthlyCostSaving()).isNull();
        assertThat(candidate.reasons()).extracting(RecommendationCandidate.Reason::code)
                .containsExactly("GOAL_SAVING_SHORTFALL", "GOAL_FORECAST_DELAY");
        assertThat(candidate.description()).contains("'비상금'").contains("250,000원");
    }

    @Test
    void savingGeneratorHandlesZeroPaceMissingFieldsAndSufficientPace() {
        Outcome zeroPace = saving.generate(input(), savingIssue(500_000L, 0L, 1_000_000L, 0L));
        assertThat(zeroPace.candidates().get(0).effect().currentForecastDate()).isNull();
        assertThat(zeroPace.candidates().get(0).effect().improvedForecastDate()).isEqualTo(BASE.plusMonths(2));
        assertThat(zeroPace.candidates().get(0).effect().advancedDays()).isNull();

        Outcome missing = saving.generate(input(), savingIssue(500_000L, 100_000L, null, null));
        assertThat(missing.candidates()).hasSize(1);
        assertThat(missing.candidates().get(0).effect().improvedForecastDate()).isNull();
        assertThat(missing.candidates().get(0).effect().contributionAmount()).isEqualTo(400_000L);
        assertThat(missing.candidates().get(0).reasons()).extracting(RecommendationCandidate.Reason::code)
                .containsExactly("GOAL_SAVING_SHORTFALL", "SNAPSHOT_FIELD_MISSING");

        assertThat(saving.generate(input(), savingIssue(300_000L, 300_000L, 1_000_000L, 0L)).candidates()).isEmpty();
        assertThat(saving.generate(input(), savingIssue(500_000L, 100_000L, 1_000_000L, 1_000_000L)).candidates()).isEmpty();

        Outcome noMetrics = saving.generate(input(), issue(1L, "GOAL", "SAVING_GOAL_RISK", IssueSeverity.LOW,
                json.createObjectNode(), json.createObjectNode().put("targetId", 7L)));
        assertThat(noMetrics.skipReason()).isEqualTo(SkipReason.INSUFFICIENT_DATA);
        assertThat(saving.supports(issue(1L, "GOAL", "SPENDING_GOAL_RISK", IssueSeverity.LOW, null, null))).isFalse();
    }

    @Test
    void baseDateFallsBackToCompletionWhenSnapshotHasNoAsOf() {
        RecommendationInput noSnapshot = new RecommendationInput(1L, 1L, "v1", 7L, AS_OF.plusDays(3), null,
                List.of(savingIssue(500_000L, 250_000L, 3_000_000L, 1_000_000L)));
        Outcome outcome = saving.generate(noSnapshot, noSnapshot.issues().get(0));
        assertThat(outcome.candidates().get(0).effect().improvedForecastDate()).isEqualTo(BASE.plusDays(3).plusMonths(4));
    }

    @Test
    void spendingGeneratorUsesOverBudgetAndDefinesAnnualAsMonthlyTimesTwelve() {
        ObjectNode metrics = json.createObjectNode().put("monthlyBudget", 1_000_000L).put("spent", 600_000L).put("projectedMonthEnd", 1_380_000L);
        ObjectNode evidence = json.createObjectNode().put("targetId", 9L);
        evidence.putObject("goalSnapshot").put("name", "식비");
        Outcome outcome = spending.generate(input(), issue(12L, "GOAL", "SPENDING_GOAL_RISK", IssueSeverity.MEDIUM, metrics, evidence));
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.candidateKey()).isEqualTo("REDUCE_SPENDING:9");
        assertThat(candidate.effect().monthlyCostSaving()).isEqualTo(380_000L);
        assertThat(candidate.effect().annualCostSaving()).isEqualTo(380_000L * 12);
        assertThat(candidate.effect().contributionAmount()).isNull();

        ObjectNode under = json.createObjectNode().put("monthlyBudget", 1_000_000L).put("spent", 100_000L).put("projectedMonthEnd", 900_000L);
        assertThat(spending.generate(input(), issue(13L, "GOAL", "SPENDING_GOAL_RISK", IssueSeverity.LOW, under, evidence)).candidates()).isEmpty();
        assertThat(spending.generate(input(), issue(14L, "GOAL", "SPENDING_GOAL_RISK", IssueSeverity.LOW, json.createObjectNode(), evidence))
                .skipReason()).isEqualTo(SkipReason.INSUFFICIENT_DATA);
    }

    @Test
    void cashflowGeneratorUsesRiskFieldsAndMarksRiskReduction() {
        ObjectNode risk = json.createObjectNode().put("riskId", 55L).put("riskDate", "2026-09-20").put("expectedBalance", -120_000L);
        ObjectNode evidence = json.createObjectNode().put("forecastId", 3L);
        Outcome outcome = cashflow.generate(input(), issue(15L, "CASHFLOW", "BALANCE_SHORTAGE", IssueSeverity.HIGH, risk, evidence));
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.candidateKey()).isEqualTo("SECURE_SHORTAGE_BUFFER:55");
        assertThat(candidate.effect().riskReduction()).isEqualTo("BALANCE_SHORTAGE_AVOIDED");
        assertThat(candidate.effect().monthlyCostSaving()).isNull();
        assertThat(candidate.title()).contains("120,000원");
        assertThat(candidate.reasons().get(0).evidence().path("shortageAmount").asLong()).isEqualTo(120_000L);
        ObjectNode broken = json.createObjectNode().put("riskId", 55L).put("riskDate", "not-a-date");
        assertThat(cashflow.generate(input(), issue(16L, "CASHFLOW", "BALANCE_SHORTAGE", IssueSeverity.HIGH, broken, evidence))
                .skipReason()).isEqualTo(SkipReason.INSUFFICIENT_DATA);
    }

    @Test
    void priorityRanksByScoreThenIssueIdAndIsUrgencyAware() {
        RecommendationInput.Issue savingIssue = savingIssue(500_000L, 250_000L, 3_000_000L, 1_000_000L); // HIGH, id 11
        ObjectNode risk = json.createObjectNode().put("riskId", 55L).put("riskDate", BASE.plusDays(10).toString()).put("expectedBalance", -120_000L);
        RecommendationInput.Issue cashIssue = issue(15L, "CASHFLOW", "BALANCE_SHORTAGE", IssueSeverity.MEDIUM, risk, json.createObjectNode().put("forecastId", 3L));
        ObjectNode metrics = json.createObjectNode().put("monthlyBudget", 1_000_000L).put("spent", 600_000L).put("projectedMonthEnd", 1_050_000L);
        RecommendationInput.Issue spendIssue = issue(12L, "GOAL", "SPENDING_GOAL_RISK", IssueSeverity.LOW, metrics, json.createObjectNode().put("targetId", 9L));
        RecommendationInput input = input(savingIssue, spendIssue, cashIssue);
        List<RecommendationCandidate> candidates = List.of(
                spending.generate(input, spendIssue).candidates().get(0),
                cashflow.generate(input, cashIssue).candidates().get(0),
                saving.generate(input, savingIssue).candidates().get(0));

        List<RecommendationPriorityPolicy.Scored> ranked = RecommendationPriorityPolicy.rank(input, candidates, BASE);
        // saving: HIGH 30 + 효과 ceil(250,000/100,000)=3 + 122일/30=4단위×5=20 + 난이도 5 = 58
        // cashflow: MEDIUM 20 + 시급 10일 → 20 = 40
        // spending: LOW 10 + 효과 1 + 난이도 5 = 16
        assertThat(ranked).extracting(s -> s.candidate().domain()).containsExactly("SAVING", "CASHFLOW", "SPENDING");
        assertThat(ranked).extracting(RecommendationPriorityPolicy.Scored::score).containsExactly(58, 40, 16);
        assertThat(ranked).extracting(RecommendationPriorityPolicy.Scored::priority).containsExactly(1, 2, 3);

        // 동점이면 issueId 오름차순
        RecommendationCandidate c = candidates.get(0);
        RecommendationCandidate sameIssueOtherKey = new RecommendationCandidate(c.generatorVersion(), "REDUCE_SPENDING:other",
                c.sourceIssueId(), c.domain(), c.type(), c.title(), c.description(), c.reasons(), c.effect());
        List<RecommendationPriorityPolicy.Scored> tie = RecommendationPriorityPolicy.rank(input, List.of(sameIssueOtherKey, c), BASE);
        assertThat(tie).extracting(RecommendationPriorityPolicy.Scored::score).containsExactly(16, 16);
        assertThat(tie.get(0).candidate().candidateKey()).isEqualTo("REDUCE_SPENDING:9");
    }
}
