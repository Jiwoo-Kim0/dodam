package com.ssafy.dodam.domain.pattern.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/patterns/me/priority-profile. 개선안 우선순위 산정(기획안 5-4)에 쓸 사용자별 가중치·집중 영역이다.
 *
 * <p>추천 엔진(API 16)은 `최종 점수 = 규칙 점수 + α × 군집 이탈 점수`의 α와 판단 기준 가중치를 여기서 가져간다. 군집 결과가 없으면 α = 0.
 *
 * @param source                PATTERN_ANALYSIS(저장된 최근 분석) | LIVE_CALCULATION(저장 결과 없이 현재 거래로 계산) | DEFAULT(데이터 없음, 기본 프로필)
 * @param analysisId            근거 분석 ID. PATTERN_ANALYSIS일 때만
 * @param basedOn               근거 시각(분석 완료 시각 또는 계산 시각)
 * @param profileType           SAVING_FOCUSED | SPENDING_FOCUSED | DEBT_FOCUSED | LIQUIDITY_FOCUSED | BALANCED
 * @param weights               판단 기준 5종 가중치. 합 1.0
 * @param clusterDeviationAlpha 군집 이탈 점수 보조 가중치 α. 군집 결과가 없으면 0
 * @param focusAreas            점검 우선 영역(점수 내림차순)
 * @param assumptions           적용한 가정·기본안
 */
public record PriorityProfileResponse(
        String source,
        Long analysisId,
        LocalDateTime basedOn,
        String profileType,
        Weights weights,
        double clusterDeviationAlpha,
        List<FocusArea> focusAreas,
        List<String> assumptions
) {

    public PriorityProfileResponse {
        focusAreas = List.copyOf(focusAreas);
        assumptions = List.copyOf(assumptions);
    }

    /** 기획안 5-4 우선순위 판단 기준. */
    public record Weights(
            double financialRisk,
            double expectedEffect,
            double goalContribution,
            double urgency,
            double executionDifficulty
    ) {
    }

    /**
     * @param area   SPENDING_REDUCTION | DEBT_REPAYMENT | SAVING_INCREASE | LIQUIDITY_BUFFER | FIXED_COST_REVIEW
     * @param score  0~1
     * @param reason 점수 근거(사람이 읽는 문장)
     */
    public record FocusArea(String area, double score, String reason) {
    }
}
