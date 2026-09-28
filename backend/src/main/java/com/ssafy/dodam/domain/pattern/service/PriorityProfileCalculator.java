package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.dto.response.PriorityProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PriorityProfileResponse.FocusArea;
import com.ssafy.dodam.domain.pattern.dto.response.PriorityProfileResponse.Weights;
import com.ssafy.dodam.domain.pattern.entity.LiquidityLevel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 추천 우선순위 프로필(517). 패턴 지표를 기획안 5-4의 판단 기준 가중치와 점검 우선 영역으로 바꾼다.
 *
 * <p>명세·기획안에 수치가 없어 아래 규칙은 **서비스 기본안**이다(`assumptions`에 그대로 적어 내보낸다).
 * <pre>
 * 기본 가중치: 위험 .25 / 효과 .25 / 목표기여 .20 / 시급성 .15 / 실행난이도 .15
 * 부채부담 ≥ 0.30      → 위험 +.10, 시급성 +.05
 * 유동성 LOW           → 위험 +.05, 시급성 +.05
 * 재량소비 ≥ 0.40 또는 소비/소득 ≥ 0.80 → 효과 +.10
 * 저축 일관성 ≥ 0.80 이고 저축률 ≥ 0.20 → 목표기여 +.10
 * 가중치는 합이 1이 되게 정규화한다.
 *
 * 집중 영역 점수(0~1):
 *   SPENDING_REDUCTION = 재량소비 비중 + max(0, 소비/소득 − 0.6)
 *   DEBT_REPAYMENT     = 부채부담 / 0.4
 *   SAVING_INCREASE    = 1 − 저축률 / 0.3
 *   LIQUIDITY_BUFFER   = LOW 1.0 / MEDIUM 0.5 / HIGH 0.0
 *   FIXED_COST_REVIEW  = (고정지출 비중 − 0.3) / 0.4
 * 프로필 유형 = 1위 영역(점수 < 0.3이면 BALANCED)
 * α(군집 이탈 보조 가중치) = 군집 결과가 있으면 0.2 × 신뢰도, 없으면 0
 * </pre>
 */
@Component
public class PriorityProfileCalculator {

    static final Weights BASE = new Weights(0.25, 0.25, 0.20, 0.15, 0.15);
    static final double CLUSTER_ALPHA_MAX = 0.2;
    static final double BALANCED_THRESHOLD = 0.3;

    public PriorityProfileResponse calculate(String source, Long analysisId, LocalDateTime basedOn, Inputs in) {
        List<String> assumptions = new ArrayList<>();
        double risk = BASE.financialRisk();
        double effect = BASE.expectedEffect();
        double goal = BASE.goalContribution();
        double urgency = BASE.urgency();
        double difficulty = BASE.executionDifficulty();
        assumptions.add("기본 가중치 위험 0.25 / 효과 0.25 / 목표기여 0.20 / 시급성 0.15 / 실행난이도 0.15 (서비스 기본안)");

        if (in.debtBurden() != null && in.debtBurden() >= 0.30) {
            risk += 0.10;
            urgency += 0.05;
            assumptions.add("부채부담 0.30 이상: 위험 +0.10, 시급성 +0.05");
        }
        if (in.liquidityLevel() == LiquidityLevel.LOW) {
            risk += 0.05;
            urgency += 0.05;
            assumptions.add("유동성 LOW: 위험 +0.05, 시급성 +0.05");
        }
        boolean heavySpending = (in.discretionaryRatio() != null && in.discretionaryRatio() >= 0.40)
                || (in.spendingToIncome() != null && in.spendingToIncome() >= 0.80);
        if (heavySpending) {
            effect += 0.10;
            assumptions.add("재량소비 비중 0.40 이상 또는 소비/소득 0.80 이상: 효과 +0.10");
        }
        if (in.savingConsistency() != null && in.savingConsistency() >= 0.80 && in.savingRate() != null && in.savingRate() >= 0.20) {
            goal += 0.10;
            assumptions.add("저축 일관성 0.80 이상·저축률 0.20 이상: 목표기여 +0.10");
        }
        double total = risk + effect + goal + urgency + difficulty;
        Weights weights = new Weights(Stats.round4(risk / total), Stats.round4(effect / total), Stats.round4(goal / total),
                Stats.round4(urgency / total), Stats.round4(difficulty / total));

        List<FocusArea> areas = new ArrayList<>();
        double spendingScore = Stats.clamp01(nz(in.discretionaryRatio()) + Math.max(0.0, nz(in.spendingToIncome()) - 0.6));
        areas.add(new FocusArea("SPENDING_REDUCTION", Stats.round4(spendingScore),
                "재량소비 비중 " + fmt(in.discretionaryRatio()) + ", 소비/소득 " + fmt(in.spendingToIncome())));
        double debtScore = Stats.clamp01(nz(in.debtBurden()) / 0.4);
        areas.add(new FocusArea("DEBT_REPAYMENT", Stats.round4(debtScore), "부채부담(월 상환/소득) " + fmt(in.debtBurden())));
        double savingScore = in.savingRate() == null ? 0.5 : Stats.clamp01(1.0 - in.savingRate() / 0.3);
        areas.add(new FocusArea("SAVING_INCREASE", Stats.round4(savingScore),
                in.savingRate() == null ? "소득을 알 수 없어 중간값 0.5 적용" : "저축률 " + fmt(in.savingRate())));
        double liquidityScore = in.liquidityLevel() == null ? 0.0
                : switch (in.liquidityLevel()) {
                    case LOW -> 1.0;
                    case MEDIUM -> 0.5;
                    case HIGH -> 0.0;
                };
        areas.add(new FocusArea("LIQUIDITY_BUFFER", liquidityScore,
                in.liquidityLevel() == null ? "계좌 정보 없음" : "유동성 " + in.liquidityLevel().name()));
        double fixedScore = Stats.clamp01((nz(in.fixedExpenseRatio()) - 0.3) / 0.4);
        areas.add(new FocusArea("FIXED_COST_REVIEW", Stats.round4(fixedScore), "고정지출 비중 " + fmt(in.fixedExpenseRatio())));
        areas.sort(Comparator.comparingDouble(FocusArea::score).reversed().thenComparing(FocusArea::area));

        FocusArea top = areas.get(0);
        String profileType = top.score() < BALANCED_THRESHOLD ? "BALANCED" : switch (top.area()) {
            case "SPENDING_REDUCTION", "FIXED_COST_REVIEW" -> "SPENDING_FOCUSED";
            case "DEBT_REPAYMENT" -> "DEBT_FOCUSED";
            case "SAVING_INCREASE" -> "SAVING_FOCUSED";
            case "LIQUIDITY_BUFFER" -> "LIQUIDITY_FOCUSED";
            default -> "BALANCED";
        };

        double alpha = in.clusterConfidence() == null ? 0.0 : Stats.round4(CLUSTER_ALPHA_MAX * in.clusterConfidence());
        assumptions.add(in.clusterConfidence() == null
                ? "군집 결과 없음: 군집 이탈 보조 가중치 α = 0"
                : "군집 이탈 보조 가중치 α = 0.2 × 신뢰도 " + fmt(in.clusterConfidence())
                        + (in.clusterDeviationScore() == null ? "" : ", 군집 이탈 점수 " + fmt(in.clusterDeviationScore())));

        return new PriorityProfileResponse(source, analysisId, basedOn, profileType, weights, alpha, areas, assumptions);
    }

    /** 데이터가 전혀 없을 때의 기본 프로필(기획안 "기본값으로 주되"). */
    public PriorityProfileResponse defaults(LocalDateTime basedOn) {
        PriorityProfileResponse base = calculate("DEFAULT", null, basedOn, Inputs.empty());
        List<String> assumptions = new ArrayList<>(base.assumptions());
        assumptions.add(0, "분석 가능한 금융 데이터가 없어 기본 프로필을 돌려준다");
        return new PriorityProfileResponse(base.source(), null, basedOn, "BALANCED", base.weights(), 0.0, base.focusAreas(),
                assumptions);
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static String fmt(Double value) {
        return value == null ? "미상" : String.format("%.2f", value);
    }

    /**
     * 계산 입력. 저장된 분석(엔티티 + featureSummary + clusterProfile) 또는 즉시 계산 결과에서 만든다.
     *
     * @param clusterConfidence     군집 신뢰도. 군집 결과가 없으면 null
     * @param clusterDeviationScore 군집 이탈 점수. 없으면 null
     */
    public record Inputs(
            Double debtBurden,
            LiquidityLevel liquidityLevel,
            Double discretionaryRatio,
            Double spendingToIncome,
            Double savingRate,
            Double savingConsistency,
            Double fixedExpenseRatio,
            Double clusterConfidence,
            Double clusterDeviationScore
    ) {
        static Inputs empty() {
            return new Inputs(null, null, null, null, null, null, null, null, null);
        }
    }
}
