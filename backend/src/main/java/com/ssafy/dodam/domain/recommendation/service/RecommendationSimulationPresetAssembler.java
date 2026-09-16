package com.ssafy.dodam.domain.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSimulationPreset;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.service.generator.SavingGoalRiskGenerator;
import com.ssafy.dodam.domain.recommendation.service.generator.SpendingGoalRiskGenerator;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
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
 *   <li>CASHFLOW/SECURE_SHORTAGE_BUFFER → 대응 유형 없음.</li>
 * </ul>
 * {@code durationMonths}는 null로 두어 시뮬레이션 기본값을 따르고, {@code accountNo}는 원문 번호를 응답에 싣지 않는 정책상 항상 null이다.
 */
@Component
public class RecommendationSimulationPresetAssembler {
    static final String SPENDING_ENDPOINT = "/api/v1/simulations/spending";
    static final String SAVING_ENDPOINT = "/api/v1/simulations/saving";
    static final String REQUIRED_MONTHLY_SAVING_FIELD = "requiredMonthlySaving";
    private final RecommendationReasonRepository reasons;

    public RecommendationSimulationPresetAssembler(RecommendationReasonRepository reasons) {
        this.reasons = reasons;
    }

    public RecommendationSimulationPreset assemble(Long userId, Recommendation recommendation) {
        return assembleAll(userId, List.of(recommendation)).get(recommendation.getRecommendationId());
    }

    /** 근거가 필요한 SAVING 추천만 골라 한 번의 쿼리로 읽는다. 목록 N건에서 N번 조회하지 않기 위해서다. */
    public Map<Long, RecommendationSimulationPreset> assembleAll(Long userId, List<Recommendation> recommendations) {
        List<Long> savingIds = recommendations.stream().filter(RecommendationSimulationPresetAssembler::isSaving)
                .map(Recommendation::getRecommendationId).toList();
        Map<Long, Long> requiredByRecommendation = new HashMap<>();
        if (!savingIds.isEmpty()) {
            for (RecommendationReason reason : reasons.findAllOwnedIn(userId, savingIds)) {
                Long required = requiredMonthlySaving(reason);
                if (required != null) {
                    requiredByRecommendation.putIfAbsent(reason.getRecommendationId(), required);
                }
            }
        }
        Map<Long, RecommendationSimulationPreset> presets = new HashMap<>();
        for (Recommendation r : recommendations) {
            presets.put(r.getRecommendationId(), build(r, requiredByRecommendation.get(r.getRecommendationId())));
        }
        return presets;
    }

    private static RecommendationSimulationPreset build(Recommendation r, Long requiredMonthlySaving) {
        if (isSpending(r)) {
            Long reduction = r.getMonthlyCostSaving();
            if (reduction == null || reduction <= 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.EFFECT_AMOUNT_MISSING);
            }
            return RecommendationSimulationPreset.of(SimulationType.SPENDING, SPENDING_ENDPOINT,
                    new SpendingSimulationRequest(null, reduction, null),
                    List.of(RecommendationSimulationPreset.REDUCTION_APPLIES_TO_TOTAL_SPENDING));
        }
        if (isSaving(r)) {
            if (requiredMonthlySaving == null || requiredMonthlySaving < 0) {
                return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.SNAPSHOT_FIELD_MISSING);
            }
            return RecommendationSimulationPreset.of(SimulationType.SAVING, SAVING_ENDPOINT,
                    new SavingSimulationRequest(null, requiredMonthlySaving, null),
                    List.of(RecommendationSimulationPreset.TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT));
        }
        return RecommendationSimulationPreset.unavailable(RecommendationSimulationPreset.NO_MATCHING_SIMULATION_TYPE);
    }

    private static boolean isSpending(Recommendation r) {
        return Objects.equals(r.getDomain(), SpendingGoalRiskGenerator.DOMAIN) && Objects.equals(r.getType(), SpendingGoalRiskGenerator.TYPE);
    }

    private static boolean isSaving(Recommendation r) {
        return Objects.equals(r.getDomain(), SavingGoalRiskGenerator.DOMAIN) && Objects.equals(r.getType(), SavingGoalRiskGenerator.TYPE);
    }

    private static Long requiredMonthlySaving(RecommendationReason reason) {
        if (!SavingGoalRiskGenerator.REASON_SHORTFALL.equals(reason.getCode()) || reason.getEvidence() == null) {
            return null;
        }
        JsonNode value = reason.getEvidence().get(REQUIRED_MONTHLY_SAVING_FIELD);
        return value == null || !value.isNumber() ? null : value.asLong();
    }
}
