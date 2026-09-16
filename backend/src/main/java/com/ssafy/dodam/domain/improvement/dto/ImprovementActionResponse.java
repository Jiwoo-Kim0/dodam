package com.ssafy.dodam.domain.improvement.dto;

import com.ssafy.dodam.domain.improvement.entity.ImprovementAction;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import java.time.LocalDateTime;

/**
 * 개선 행동 1건(API 19 #2 목록 · #3 상세 공통).
 *
 * <p>{@code expected*}는 추천이 계산해 둔 예상치, {@code actual*}은 실행 후 실제 데이터로 측정한 값이다.
 * <b>둘을 합치지 않는다.</b> 측정 전이면 {@code actual*}과 {@code measuredAt}이 null이다 — 0원이 아니라
 * "아직 모름"이다.
 *
 * <p>{@code goalId}·{@code domain}·{@code type}은 연결된 추천에서 읽는다. 행동 행에 복사해 두면 두 행이
 * 어긋날 수 있어 저장하지 않는다.
 */
public record ImprovementActionResponse(
        Long actionId,
        Long recommendationId,
        Long goalId,
        String domain,
        String type,
        String title,
        String status,
        LocalDateTime plannedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        Long expectedMonthlySaving,
        Long actualMonthlySaving,
        LocalDateTime measuredAt
) {

    public static ImprovementActionResponse of(ImprovementAction action, Recommendation recommendation) {
        return new ImprovementActionResponse(
                action.getActionId(),
                action.getRecommendationId(),
                recommendation == null ? null : recommendation.getGoalId(),
                recommendation == null ? null : recommendation.getDomain(),
                recommendation == null ? null : recommendation.getType(),
                action.getTitle(),
                action.getStatus().name(),
                action.getPlannedAt(),
                action.getStartedAt(),
                action.getCompletedAt(),
                action.getCanceledAt(),
                recommendation == null ? null : recommendation.getMonthlyCostSaving(),
                action.getMonthlyCostSaving(),
                action.getMeasuredAt()
        );
    }
}
