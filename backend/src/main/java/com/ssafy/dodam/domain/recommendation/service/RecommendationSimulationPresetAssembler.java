package com.ssafy.dodam.domain.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSimulationPreset;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.service.generator.CategorySpendingIncreaseGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.HighInterestLoanGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SavingGoalRiskGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SpendingGoalRiskGenerator;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationBaselineReader;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 추천 → 시뮬레이션 프리셋 조립(S15P21D105-587). 저장된 추천·근거만 읽고 현재 데이터로 재계산하지 않는다.
 *
 * <p>유형 대응은 생성기 3종에 고정한다.
 * <ul>
 *   <li>SPENDING/REDUCE_SPENDING → {@code POST /simulations/spending}, {@code monthlyReduction = effect.monthlyCostSaving}.
 *       진단 SPENDING_GOAL_RISK는 목표 예산 전체 기준이라 카테고리를 지정하지 않는다.</li>
 *   <li>SAVING/INCREASE_MONTHLY_SAVING → {@code POST /simulations/saving}, {@code monthlySavingAmount = R}(목표 필요 월 저축액).
 *       추천 효과에는 {@code contributionAmount = R − A}만 있고 시뮬레이션은 변경 후 절대값을 받으므로, 생성기가 근거
 *       {@code GOAL_SAVING_SHORTFALL}의 evidence에 남긴 {@code requiredMonthlySaving}을 읽는다. 시뮬레이션 기준선의 현재 저축액이
 *       진단 시점 A와 달라도 목표에 필요한 R은 같으므로 델타보다 절대값이 안전하다.</li>
 *   <li>SPENDING/REDUCE_CATEGORY_SPENDING(627) → {@code POST /simulations/spending}, {@code category}는 근거
 *       {@code CATEGORY_SPENDING_INCREASE}의 evidence {@code categoryCode}, {@code monthlyReduction = effect.monthlyCostSaving}.
 *       (631) 감소액이 시뮬레이션 기준선의 카테고리 월평균을 넘으면 그 값으로 잘라 {@code REDUCTION_CAPPED_TO_SIMULATION_BASELINE}을 남긴다.
 *       이 유형만은 "저장된 값만 읽는다"는 원칙의 예외로 기준선을 한 번 읽는다 — 그대로 보내면 400이 되기 때문이다.</li>
 *   <li>LOAN/REFINANCE_LOAN(627) → {@code POST /simulations/loan/refinance}, {@code targetProductId}는 근거
 *       {@code REFINANCE_INTEREST_SAVING}의 evidence, {@code refinanceAmount = null}(잔액 전액). 절감액을 추정하지 못한 추천(대상 상품
 *       없음)은 {@code REFINANCE_TARGET_MISSING}. 대출은 evidence의 {@code loanId}로 지정한다(2026-09-16 명세 17 본문 확장) —
 *       FE는 마스킹 번호만 가져 {@code accountNo}를 만들 수 없기 때문이다. evidence에 {@code loanId}가 없으면 {@code SNAPSHOT_FIELD_MISSING}.</li>
 *   <li>CASHFLOW/SECURE_SHORTAGE_BUFFER → 대응 유형 없음.</li>
 * </ul>
 * {@code durationMonths}는 null로 두어 시뮬레이션 기본값을 따르고, {@code accountNo}는 원문 번호를 응답에 싣지 않는 정책상 항상 null이다.
 * 저축 프리셋의 대상 목표는 {@code goalId}(619)로 넘긴다 — SAVING 추천은 생성기가 진단 evidence의 targetId를 {@code Recommendation.goalId}에
 * 채워야만 만들어지므로 과거 추천에도 항상 있다. 없으면 방어적으로 전체 배분 가정({@code TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT})을 남긴다.
 */
@Component
public class RecommendationSimulationPresetAssembler {
    static final String SPENDING_ENDPOINT = "/api/v1/simulations/spending";
    static final String SAVING_ENDPOINT = "/api/v1/simulations/saving";
    static final String LOAN_REFINANCE_ENDPOINT = "/api/v1/simulations/loan/refinance";
    static final String REQUIRED_MONTHLY_SAVING_FIELD = "requiredMonthlySaving";
    private final RecommendationReasonRepository reasons;
    private final SimulationBaselineReader baselines;
    private final SimulationClock clock;

    public RecommendationSimulationPresetAssembler(RecommendationReasonRepository reasons, SimulationBaselineReader baselines,
            SimulationClock clock) {
        this.reasons = reasons;
        this.baselines = baselines;
        this.clock = clock;
    }

    public RecommendationSimulationPreset assemble(Long userId, Recommendation recommendation) {
        return assembleAll(userId, List.of(recommendation)).get(recommendation.getRecommendationId());
    }

    /**
     * 근거가 필요한 추천(SAVING·카테고리 소비·대환)만 골라 한 번의 쿼리로 읽는다. 목록 N건에서 N번 조회하지 않기 위해서다.
     * 카테고리 소비 추천이 하나라도 있으면 시뮬레이션 기준선도 한 번 읽어 감소액 상한({@link #capToBaseline})에 쓴다.
     */
    public Map<Long, RecommendationSimulationPreset> assembleAll(Long userId, List<Recommendation> recommendations) {
        List<Long> needingReasons = recommendations.stream()
                .filter(r -> isSaving(r) || isCategorySpending(r) || isRefinance(r))
                .map(Recommendation::getRecommendationId).toList();
        Map<Long, JsonNode> evidenceByRecommendation = new HashMap<>();
        if (!needingReasons.isEmpty()) {
            for (RecommendationReason reason : reasons.findAllOwnedIn(userId, needingReasons)) {
                if (reason.getEvidence() != null && isPresetReason(reason.getCode())) {
                    evidenceByRecommendation.putIfAbsent(reason.getRecommendationId(), reason.getEvidence());
                }
            }
        }
        Map<String, Long> categoryBaseline = recommendations.stream().anyMatch(RecommendationSimulationPresetAssembler::isCategorySpending)
                ? baselines.read(userId, clock.now().toLocalDate()).spendingByCategory()
                : Map.of();
        Map<Long, RecommendationSimulationPreset> presets = new HashMap<>();
        for (Recommendation r : recommendations) {
            presets.put(r.getRecommendationId(), build(r, evidenceByRecommendation.get(r.getRecommendationId()), categoryBaseline));
        }
        return presets;
    }

    /** 유형마다 프리셋 값이 들어 있는 근거 코드는 하나뿐이라 코드로만 고른다. */
    private static boolean isPresetReason(String code) {
        return SavingGoalRiskGenerator.REASON_SHORTFALL.equals(code) || CategorySpendingIncreaseGenerator.REASON_INCREASE.equals(code)
                || HighInterestLoanGenerator.REASON_INTEREST_SAVING.equals(code);
    }

    private static RecommendationSimulationPreset build(Recommendation r, JsonNode evidence, Map<String, Long> categoryBaseline) {
        if (isSpending(r)) {
            Long reduction = r.getMonthlyCostSaving();
            if (reduction == null || reduction <= 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.EFFECT_AMOUNT_MISSING);
            }
            return RecommendationSimulationPreset.of(SimulationType.SPENDING, SPENDING_ENDPOINT,
                    new SpendingSimulationRequest(null, reduction, null),
                    List.of(RecommendationSimulationPreset.REDUCTION_APPLIES_TO_TOTAL_SPENDING));
        }
        if (isCategorySpending(r)) {
            Long reduction = r.getMonthlyCostSaving();
            if (reduction == null || reduction <= 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.EFFECT_AMOUNT_MISSING);
            }
            String category = textOf(evidence, CategorySpendingIncreaseGenerator.CATEGORY_CODE_FIELD);
            if (category == null) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
            }
            return capToBaseline(category, reduction, categoryBaseline.getOrDefault(category, 0L));
        }
        if (isSaving(r)) {
            Long requiredMonthlySaving = longOf(evidence, REQUIRED_MONTHLY_SAVING_FIELD);
            if (requiredMonthlySaving == null || requiredMonthlySaving < 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
            }
            return RecommendationSimulationPreset.of(SimulationType.SAVING, SAVING_ENDPOINT,
                    new SavingSimulationRequest(null, r.getGoalId(), requiredMonthlySaving, null),
                    r.getGoalId() == null
                            ? List.of(RecommendationSimulationPreset.TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT)
                            : List.of());
        }
        if (isRefinance(r)) {
            Long targetProductId = longOf(evidence, HighInterestLoanGenerator.TARGET_PRODUCT_ID_FIELD);
            if (targetProductId == null || targetProductId <= 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.REFINANCE_TARGET_MISSING);
            }
            Long loanId = longOf(evidence, HighInterestLoanGenerator.LOAN_ID_FIELD);
            if (loanId == null || loanId <= 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
            }
            return RecommendationSimulationPreset.of(SimulationType.LOAN_REFINANCE, LOAN_REFINANCE_ENDPOINT,
                    new LoanRefinanceSimulationRequest(null, loanId, targetProductId, null), List.of());
        }
        return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.NO_MATCHING_SIMULATION_TYPE);
    }

    /**
     * (631) 카테고리 소비 감소 프리셋은 시뮬레이션이 검사하는 상한(직전 3개월 카테고리 월평균, {@code SpendingReductionCalculator})을 넘지 않게
     * 자른다. 진단은 지난달 vs 직전 3개월 평균으로 증가액을 잡으므로, 6·7월 소비가 거의 없고 8월만 튄 카테고리에서는 증가액이 6~8월 평균보다
     * 클 수 있다. 그대로 보내면 400 {@code SIMULATION_REDUCTION_EXCEEDS_SPENDING}이다. 627 최초 구현(3개월 vs 3개월)은 진단 최근 기간과
     * 시뮬레이션 창이 같아 이 일이 없었다. 기준선에 그 카테고리 소비가 없으면 어떤 값도 400이라 프리셋을 만들지 않는다.
     */
    static RecommendationSimulationPreset capToBaseline(String category, long reduction, long baselineSpending) {
        if (baselineSpending <= 0) {
            return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.SIMULATION_BASELINE_UNAVAILABLE);
        }
        if (reduction > baselineSpending) {
            return RecommendationSimulationPreset.of(SimulationType.SPENDING, SPENDING_ENDPOINT,
                    new SpendingSimulationRequest(category, baselineSpending, null),
                    List.of(RecommendationSimulationPreset.REDUCTION_CAPPED_TO_SIMULATION_BASELINE));
        }
        return RecommendationSimulationPreset.of(SimulationType.SPENDING, SPENDING_ENDPOINT,
                new SpendingSimulationRequest(category, reduction, null), List.of());
    }

    private static boolean isSpending(Recommendation r) {
        return Objects.equals(r.getDomain(), SpendingGoalRiskGenerator.DOMAIN) && Objects.equals(r.getType(), SpendingGoalRiskGenerator.TYPE);
    }

    private static boolean isCategorySpending(Recommendation r) {
        return Objects.equals(r.getDomain(), CategorySpendingIncreaseGenerator.DOMAIN)
                && Objects.equals(r.getType(), CategorySpendingIncreaseGenerator.TYPE);
    }

    private static boolean isSaving(Recommendation r) {
        return Objects.equals(r.getDomain(), SavingGoalRiskGenerator.DOMAIN) && Objects.equals(r.getType(), SavingGoalRiskGenerator.TYPE);
    }

    private static boolean isRefinance(Recommendation r) {
        return Objects.equals(r.getDomain(), HighInterestLoanGenerator.DOMAIN) && Objects.equals(r.getType(), HighInterestLoanGenerator.TYPE);
    }

    private static Long longOf(JsonNode evidence, String field) {
        JsonNode value = evidence == null ? null : evidence.get(field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    private static String textOf(JsonNode evidence, String field) {
        JsonNode value = evidence == null ? null : evidence.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText();
    }
}
