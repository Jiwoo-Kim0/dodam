package com.ssafy.dodam.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 상태 변경 이력 1건.
 *
 * <p>ERD대로 변경 <b>후</b> 상태만 저장한다. 이전 상태 컬럼은 없으며, 직전 이력 행에서 읽는다.
 * 새 추천은 생성 시점에 PENDING 이력 1건을 남겨 첫 상태 변경 전에도 이력 조회가 비지 않게 한다.
 * 상태 변경 시 행을 추가하는 것은 549의 몫이다.
 */
@Getter
@Entity
@Table(name = "recommendation_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationHistory {

    public static final int TEXT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_history_id")
    private Long recommendationHistoryId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RecommendationStatus status;

    @Column(name = "reason", length = TEXT_MAX_LENGTH)
    private String reason;

    @Column(name = "memo", length = TEXT_MAX_LENGTH)
    private String memo;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    private RecommendationHistory(Long recommendationId, RecommendationStatus status,
            String reason, String memo, LocalDateTime changedAt) {
        this.recommendationId = recommendationId;
        this.status = status;
        this.reason = reason;
        this.memo = memo;
        this.changedAt = changedAt;
    }

    /** 생성 직후의 초기 이력. reason/memo는 비운다. */
    public static RecommendationHistory initial(Long recommendationId, LocalDateTime now) {
        return of(recommendationId, RecommendationStatus.PENDING, null, null, now);
    }

    public static RecommendationHistory of(Long recommendationId, RecommendationStatus status,
            String reason, String memo, LocalDateTime changedAt) {
        if (recommendationId == null) {
            throw new IllegalArgumentException("추천을 지정해야 합니다.");
        }
        if (status == null || changedAt == null) {
            throw new IllegalArgumentException("상태와 변경 시각이 필요합니다.");
        }
        requireLength("reason", reason);
        requireLength("memo", memo);
        return new RecommendationHistory(recommendationId, status, reason, memo, changedAt);
    }

    private static void requireLength(String name, String value) {
        if (value != null && value.length() > TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException(name + "은(는) " + TEXT_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
    }
}
