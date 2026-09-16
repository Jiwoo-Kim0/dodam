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
import com.ssafy.dodam.domain.recommendation.service.RecommendationTransitionPolicy;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.service.generator.CashflowShortageGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.CategorySpendingIncreaseGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.HighInterestLoanGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SavingGoalRiskGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SpendingGoalRiskGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 생성기·우선순위·전이표를 DB 없이 검증한다. 문제 JSON은 DiagnosisEvaluator가 저장하는 형식을 손으로 만든 것이며,
 * 실제 진단 실행으로 만든 형식과의 정합은 RecommendationApiTest가 맡는다.
 */
class RecommendationGeneratorTest {
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 9, 13, 12, 0);
    private static final LocalDate BASE = AS_OF.toLocalDate();
    private final ObjectMapper json = new ObjectMapper();
    private final SavingGoalRiskGenerator saving = new SavingGoalRiskGenerator(json);
    private final SpendingGoalRiskGenerator spending = new SpendingGoalRiskGenerator(json);
    private final CashflowShortageGenerator cashflow = new CashflowShortageGenerator(json);
    private final CategorySpendingIncreaseGenerator categorySpending = new CategorySpendingIncreaseGenerator(json);
    private final HighInterestLoanGenerator highInterestLoan = new HighInterestLoanGenerator(json);

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
        // 제목은 할 행동, 금액은 설명·효과 필드가 보여준다(S15P21D105-604)
        assertThat(candidate.title()).isEqualTo("출금 전 잔액 채워두기");
        assertThat(candidate.description()).contains("120,000원").endsWith("좋겠어요.");
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

    private RecommendationInput.Issue issue(long id, String domain, String type, IssueSeverity severity, JsonNode current,
            JsonNode benchmark, JsonNode evidence) {
        return new RecommendationInput.Issue(id, domain, type, severity, "t", "d", current, benchmark, evidence, DetectionSource.RULE, null, null);
    }

    @Test
    void categorySpendingGeneratorRestoresPreviousAverageAndKeepsCategoryCode() {
        ObjectNode current = json.createObjectNode().put("categoryId", 3L).put("categoryCode", "FOOD").put("categoryName", "식비")
                .put("monthlyAverage", 140_000L).put("increaseAmount", 40_000L).put("increaseRatePercent", 40.0).put("consecutiveMonths", 3);
        ObjectNode benchmark = json.createObjectNode().put("previousMonthlyAverage", 100_000L);
        ObjectNode evidence = json.createObjectNode().put("targetId", 3L);
        Outcome outcome = categorySpending.generate(input(), issue(21L, "SPENDING", "CATEGORY_SPENDING_INCREASE", IssueSeverity.MEDIUM,
                current, benchmark, evidence));
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.candidateKey()).isEqualTo("REDUCE_CATEGORY_SPENDING:3");
        assertThat(candidate.domain()).isEqualTo("SPENDING");
        assertThat(candidate.sourceIssueId()).isEqualTo(21L);
        assertThat(candidate.effect().monthlyCostSaving()).isEqualTo(40_000L);
        assertThat(candidate.effect().annualCostSaving()).isEqualTo(480_000L);
        assertThat(candidate.title()).isEqualTo("식비 지출 줄여보기");
        assertThat(candidate.description()).isEqualTo("이전 수준으로 되돌리면 월 40,000원을 아낄 수 있어요.");
        assertThat(candidate.reasons()).hasSize(1);
        assertThat(candidate.reasons().get(0).code()).isEqualTo("CATEGORY_SPENDING_INCREASE");
        assertThat(candidate.reasons().get(0).evidence().path("categoryCode").asText()).isEqualTo("FOOD");
        assertThat(candidate.reasons().get(0).evidence().path("consecutiveMonths").asInt()).isEqualTo(3);

        ObjectNode flat = current.deepCopy().put("increaseAmount", 0L);
        assertThat(categorySpending.generate(input(), issue(22L, "SPENDING", "CATEGORY_SPENDING_INCREASE", IssueSeverity.LOW, flat, benchmark, evidence))
                .candidates()).isEmpty();
        assertThat(categorySpending.generate(input(), issue(23L, "SPENDING", "CATEGORY_SPENDING_INCREASE", IssueSeverity.LOW, current, null, evidence))
                .skipReason()).isEqualTo(SkipReason.INSUFFICIENT_DATA);
        assertThat(categorySpending.supports(issue(1L, "GOAL", "SPENDING_GOAL_RISK", IssueSeverity.LOW, null, null))).isFalse();
    }

    private ObjectNode loanCurrent(boolean withEstimate) {
        ObjectNode current = json.createObjectNode().put("loanId", 5L).put("accountName", "신용대출").put("balance", 1_200_000L)
                .put("interestRate", new java.math.BigDecimal("12.0000")).put("referenceRate", new java.math.BigDecimal("7.0000"))
                .put("rateDifference", new java.math.BigDecimal("5.0000")).put("comparisonGroup", "CREDIT");
        ObjectNode refinance = current.putObject("refinance").put("available", withEstimate).put("feesIncluded", false);
        if (withEstimate) {
            refinance.put("targetProductId", 40L).put("targetProductName", "A 신용대출").put("appliedRate", new java.math.BigDecimal("6.0000"))
                    .put("remainingInstallments", 3).put("beforeInterest", 36_000L).put("afterInterest", 18_000L).put("interestSaving", 18_000L)
                    .put("monthlySaving", 6_000L);
        } else {
            refinance.put("unavailableReason", "NO_ELIGIBLE_PRODUCT");
        }
        return current;
    }

    @Test
    void highInterestLoanGeneratorUsesDiagnosisEstimateAndCapsAnnualByRemainingInstallments() {
        ObjectNode evidence = json.createObjectNode().put("targetId", 5L);
        Outcome outcome = highInterestLoan.generate(input(), issue(31L, "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.HIGH, loanCurrent(true), null, evidence));
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.candidateKey()).isEqualTo("REFINANCE_LOAN:5");
        assertThat(candidate.domain()).isEqualTo("LOAN");
        assertThat(candidate.type()).isEqualTo("REFINANCE_LOAN");
        assertThat(candidate.effect().interestSaving()).isEqualTo(18_000L);
        assertThat(candidate.effect().monthlyCostSaving()).isEqualTo(6_000L);
        // 잔여 3회 → 연간은 ×12가 아니라 ×3
        assertThat(candidate.effect().annualCostSaving()).isEqualTo(18_000L);
        assertThat(candidate.title()).isEqualTo("'신용대출' 대환 알아보기");
        // 설명은 한 문장 + 효과 금액만 남겼다(S15P21D105-604). 금리 비교는 화면의 진단 문제 설명과
        // 아래 근거가 이미 보여주므로, 카드에서 반복하면 같은 숫자가 한 화면에 세 번 나온다.
        assertThat(candidate.description()).isEqualTo("대환하면 만기까지 이자 18,000원을 아낄 수 있어요(수수료 제외).");
        assertThat(candidate.reasons().get(0).description()).contains("연 12%").contains("7%").contains("5%p");
        assertThat(candidate.reasons()).extracting(RecommendationCandidate.Reason::code)
                .containsExactly("LOAN_RATE_ABOVE_MARKET", "REFINANCE_INTEREST_SAVING");
        assertThat(candidate.reasons().get(1).evidence().path("targetProductId").asLong()).isEqualTo(40L);
        assertThat(candidate.reasons().get(1).evidence().path("feesIncluded").asBoolean()).isFalse();
    }

    @Test
    void highInterestLoanGeneratorWithoutEstimateStillRecommendsComparison() {
        ObjectNode evidence = json.createObjectNode().put("targetId", 5L);
        Outcome outcome = highInterestLoan.generate(input(), issue(32L, "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.MEDIUM, loanCurrent(false), null, evidence));
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.candidateKey()).isEqualTo("REFINANCE_LOAN:5");
        assertThat(candidate.effect()).isEqualTo(Recommendation.Effect.NONE);
        assertThat(candidate.title()).isEqualTo("'신용대출' 대환 조건 비교해보기");
        assertThat(candidate.reasons()).extracting(RecommendationCandidate.Reason::code)
                .containsExactly("LOAN_RATE_ABOVE_MARKET", "REFINANCE_ESTIMATE_UNAVAILABLE");
        assertThat(candidate.reasons().get(1).evidence().path("reason").asText()).isEqualTo("NO_ELIGIBLE_PRODUCT");

        ObjectNode noRate = loanCurrent(true);
        noRate.remove("referenceRate");
        assertThat(highInterestLoan.generate(input(), issue(33L, "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.MEDIUM, noRate, null, evidence))
                .skipReason()).isEqualTo(SkipReason.INSUFFICIENT_DATA);
        assertThat(highInterestLoan.supports(issue(1L, "CASHFLOW", "BALANCE_SHORTAGE", IssueSeverity.LOW, null, null))).isFalse();
    }

    @Test
    void transitionTableMatchesSpec() {
        assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.PENDING, RecommendationStatus.ACCEPTED)).isTrue();
        assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.PENDING, RecommendationStatus.COMPLETED)).isFalse();
        assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.PENDING, RecommendationStatus.PENDING)).isFalse();
        assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.DEFERRED, RecommendationStatus.DEFERRED)).isFalse();
        assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.ACCEPTED, RecommendationStatus.COMPLETED)).isTrue();
        assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.ACCEPTED, RecommendationStatus.DEFERRED)).isTrue();
        for (RecommendationStatus to : RecommendationStatus.values()) {
            assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.REJECTED, to)).isFalse();
            assertThat(RecommendationTransitionPolicy.canTransition(RecommendationStatus.COMPLETED, to)).isFalse();
        }
    }
}
