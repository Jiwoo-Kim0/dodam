package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSimulationPreset;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.service.RecommendationSimulationPresetAssembler;
import com.ssafy.dodam.domain.recommendation.service.generator.CategorySpendingIncreaseGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.HighInterestLoanGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SavingGoalRiskGenerator;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 587 추천 → 시뮬레이션 프리셋. 저장된 추천·근거만으로 조립하고 근거 조회는 SAVING 추천이 있을 때 한 번만 한다. */
class RecommendationSimulationPresetAssemblerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final String KEY = "0".repeat(64);
    private final ObjectMapper json = new ObjectMapper();
    private final RecommendationReasonRepository reasons = mock(RecommendationReasonRepository.class);
    private final RecommendationSimulationPresetAssembler assembler = new RecommendationSimulationPresetAssembler(reasons);

    @Test
    void spendingPresetUsesMonthlyCostSavingWithoutCategory() {
        Recommendation r = recommendation(1L, "SPENDING", "REDUCE_SPENDING",
                new Recommendation.Effect(120_000L, 1_440_000L, null, null, null, null, null, null));

        RecommendationSimulationPreset preset = assembler.assemble(7L, r);

        assertThat(preset.available()).isTrue();
        assertThat(preset.type()).isEqualTo(SimulationType.SPENDING);
        assertThat(preset.endpoint()).isEqualTo("/api/v1/simulations/spending");
        assertThat(preset.request()).isEqualTo(new SpendingSimulationRequest(null, 120_000L, null));
        assertThat(preset.assumptions()).containsExactly(RecommendationSimulationPreset.REDUCTION_APPLIES_TO_TOTAL_SPENDING);
        verify(reasons, never()).findAllOwnedIn(anyLong(), any());
    }

    @Test
    void spendingPresetIsUnavailableWithoutPositiveAmount() {
        Recommendation r = recommendation(1L, "SPENDING", "REDUCE_SPENDING", Recommendation.Effect.NONE);

        RecommendationSimulationPreset preset = assembler.assemble(7L, r);

        assertThat(preset.available()).isFalse();
        assertThat(preset.unavailableReason()).isEqualTo(RecommendationSimulationPreset.EFFECT_AMOUNT_MISSING);
        assertThat(preset.type()).isNull();
        assertThat(preset.request()).isNull();
    }

    @Test
    void savingPresetReadsRequiredMonthlySavingFromShortfallReason() {
        Recommendation r = recommendation(2L, "SAVING", "INCREASE_MONTHLY_SAVING",
                new Recommendation.Effect(null, null, null, null, null, null, null, 300_000L));
        ObjectNode evidence = json.createObjectNode();
        evidence.put("requiredMonthlySaving", 500_000L).put("averageMonthlySaving", 200_000L).put("shortfall", 300_000L);
        given(reasons.findAllOwnedIn(7L, List.of(2L))).willReturn(List.of(
                RecommendationReason.of(2L, "GOAL_FORECAST_DELAY", "무관한 근거", json.createObjectNode()),
                RecommendationReason.of(2L, SavingGoalRiskGenerator.REASON_SHORTFALL, "부족", evidence)));

        RecommendationSimulationPreset preset = assembler.assemble(7L, r);

        assertThat(preset.available()).isTrue();
        assertThat(preset.type()).isEqualTo(SimulationType.SAVING);
        assertThat(preset.endpoint()).isEqualTo("/api/v1/simulations/saving");
        // 변경 후 절대값 R이지 contributionAmount(R − A)가 아니다. accountNo는 항상 null, 대상 목표는 추천의 goalId(619)
        assertThat(preset.request()).isEqualTo(new SavingSimulationRequest(null, 3L, 500_000L, null));
        assertThat(preset.assumptions()).isEmpty();
    }

    @Test
    void savingPresetIsUnavailableWhenShortfallEvidenceIsMissing() {
        Recommendation r = recommendation(2L, "SAVING", "INCREASE_MONTHLY_SAVING",
                new Recommendation.Effect(null, null, null, null, null, null, null, 300_000L));
        ObjectNode withoutRequired = json.createObjectNode();
        withoutRequired.put("shortfall", 300_000L);
        given(reasons.findAllOwnedIn(7L, List.of(2L))).willReturn(List.of(
                RecommendationReason.of(2L, SavingGoalRiskGenerator.REASON_SHORTFALL, "부족", withoutRequired)));

        RecommendationSimulationPreset preset = assembler.assemble(7L, r);

        assertThat(preset.available()).isFalse();
        assertThat(preset.unavailableReason()).isEqualTo(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
    }

    @Test
    void cashflowAndUnknownTypesHaveNoPreset() {
        Recommendation cashflow = recommendation(3L, "CASHFLOW", "SECURE_SHORTAGE_BUFFER",
                new Recommendation.Effect(null, null, null, "BALANCE_SHORTAGE_AVOIDED", null, null, null, null));
        Recommendation unknown = recommendation(4L, "LOAN", "REFINANCE", Recommendation.Effect.NONE);

        Map<Long, RecommendationSimulationPreset> presets = assembler.assembleAll(7L, List.of(cashflow, unknown));

        assertThat(presets.get(3L).available()).isFalse();
        assertThat(presets.get(3L).unavailableReason()).isEqualTo(RecommendationSimulationPreset.NO_MATCHING_SIMULATION_TYPE);
        assertThat(presets.get(4L).unavailableReason()).isEqualTo(RecommendationSimulationPreset.NO_MATCHING_SIMULATION_TYPE);
        assertThat(presets.get(3L).assumptions()).isEmpty();
        verify(reasons, never()).findAllOwnedIn(anyLong(), any());
    }

    @Test
    void assembleAllQueriesReasonsOnceForSavingRecommendationsOnly() {
        Recommendation spending = recommendation(1L, "SPENDING", "REDUCE_SPENDING",
                new Recommendation.Effect(10_000L, 120_000L, null, null, null, null, null, null));
        Recommendation savingA = recommendation(2L, "SAVING", "INCREASE_MONTHLY_SAVING", Recommendation.Effect.NONE);
        Recommendation savingB = recommendation(5L, "SAVING", "INCREASE_MONTHLY_SAVING", Recommendation.Effect.NONE);
        ObjectNode evidenceA = json.createObjectNode();
        evidenceA.put("requiredMonthlySaving", 400_000L);
        given(reasons.findAllOwnedIn(7L, List.of(2L, 5L))).willReturn(List.of(
                RecommendationReason.of(2L, SavingGoalRiskGenerator.REASON_SHORTFALL, "부족", evidenceA)));

        Map<Long, RecommendationSimulationPreset> presets = assembler.assembleAll(7L, List.of(spending, savingA, savingB));

        verify(reasons, times(1)).findAllOwnedIn(7L, List.of(2L, 5L));
        assertThat(presets).hasSize(3);
        assertThat(presets.get(1L).type()).isEqualTo(SimulationType.SPENDING);
        assertThat(presets.get(2L).request()).isEqualTo(new SavingSimulationRequest(null, 3L, 400_000L, null));
        assertThat(presets.get(5L).unavailableReason()).isEqualTo(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
    }

    @Test
    void categorySpendingPresetCarriesCategoryCodeFromReason() {
        Recommendation r = recommendation(6L, "SPENDING", "REDUCE_CATEGORY_SPENDING",
                new Recommendation.Effect(40_000L, 480_000L, null, null, null, null, null, null));
        ObjectNode evidence = json.createObjectNode();
        evidence.put("categoryCode", "FOOD").put("increaseAmount", 40_000L);
        given(reasons.findAllOwnedIn(7L, List.of(6L))).willReturn(List.of(
                RecommendationReason.of(6L, CategorySpendingIncreaseGenerator.REASON_INCREASE, "증가", evidence)));

        RecommendationSimulationPreset preset = assembler.assemble(7L, r);

        assertThat(preset.available()).isTrue();
        assertThat(preset.type()).isEqualTo(SimulationType.SPENDING);
        assertThat(preset.request()).isEqualTo(new SpendingSimulationRequest("FOOD", 40_000L, null));
        assertThat(preset.assumptions()).isEmpty();

        given(reasons.findAllOwnedIn(7L, List.of(6L))).willReturn(List.of(
                RecommendationReason.of(6L, CategorySpendingIncreaseGenerator.REASON_INCREASE, "증가", json.createObjectNode())));
        assertThat(assembler.assemble(7L, r).unavailableReason()).isEqualTo(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
        Recommendation noAmount = recommendation(6L, "SPENDING", "REDUCE_CATEGORY_SPENDING", Recommendation.Effect.NONE);
        assertThat(assembler.assemble(7L, noAmount).unavailableReason()).isEqualTo(RecommendationSimulationPreset.EFFECT_AMOUNT_MISSING);
    }

    @Test
    void refinancePresetUsesTargetProductFromReasonAndLeavesAccountNoToClient() {
        Recommendation r = recommendation(8L, "LOAN", "REFINANCE_LOAN",
                new Recommendation.Effect(6_000L, 18_000L, 18_000L, null, null, null, null, null));
        ObjectNode evidence = json.createObjectNode();
        evidence.put("loanId", 5L).put("targetProductId", 40L).put("interestSaving", 18_000L);
        given(reasons.findAllOwnedIn(7L, List.of(8L))).willReturn(List.of(
                RecommendationReason.of(8L, HighInterestLoanGenerator.REASON_ABOVE_MARKET, "고금리", json.createObjectNode().put("loanId", 5L)),
                RecommendationReason.of(8L, HighInterestLoanGenerator.REASON_INTEREST_SAVING, "절감", evidence)));

        RecommendationSimulationPreset preset = assembler.assemble(7L, r);

        assertThat(preset.available()).isTrue();
        assertThat(preset.type()).isEqualTo(SimulationType.LOAN_REFINANCE);
        assertThat(preset.endpoint()).isEqualTo("/api/v1/simulations/loan/refinance");
        assertThat(preset.request()).isEqualTo(new LoanRefinanceSimulationRequest(null, 40L, null));
        assertThat(preset.assumptions()).containsExactly(RecommendationSimulationPreset.ACCOUNT_NO_REQUIRED_BY_CLIENT);

        // 절감액을 추정하지 못한 추천은 대상 상품 근거가 없다
        given(reasons.findAllOwnedIn(7L, List.of(8L))).willReturn(List.of(
                RecommendationReason.of(8L, HighInterestLoanGenerator.REASON_ESTIMATE_UNAVAILABLE, "불가", json.createObjectNode())));
        RecommendationSimulationPreset unavailable = assembler.assemble(7L, r);
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.unavailableReason()).isEqualTo(RecommendationSimulationPreset.REFINANCE_TARGET_MISSING);
    }

    private static Recommendation recommendation(long id, String domain, String type, Recommendation.Effect effect) {
        Recommendation r = Recommendation.pending(7L, 11L, 3L, null, domain, type, "제목", "설명", 1, effect, KEY, NOW);
        ReflectionTestUtils.setField(r, "recommendationId", id);
        return r;
    }
}
