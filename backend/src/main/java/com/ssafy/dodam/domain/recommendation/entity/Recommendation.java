package com.ssafy.dodam.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개선안(추천) 1건.
 *
 * <p>하위행 `recommendation_reason`·`recommendation_history`는 연관관계로 매핑하지 않고 `recommendationId`
 * 값으로만 잇는다. 근거·이력은 각각 별도 API(548·549)로 조회하므로 헤더를 읽을 때 컬렉션을 끌고 올 이유가
 * 없다. `diagnosisId`·`goalId`도 값으로 둔다(diagnosis가 goal을 다루는 방식과 같다).
 *
 * <p>{@code @UniqueConstraint}를 엔티티에도 선언한다. H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기
 * 때문에, 여기 없으면 `(user_id, generation_key)` 중복 방지가 테스트에서 검증되지 않는다. MySQL CHECK
 * (priority > 0, 금액 ≥ 0)는 H2에 만들어지지 않으므로 생성자에서 먼저 막는다.
 *
 * <p>효과 컬럼 8종은 모두 nullable이다. 547은 효과를 계산하지 않으므로 null로 저장되고, 미계산을 0원으로
 * 채우지 않는다. 계산 계약은 548이 정한다.
 */
@Getter
@Entity
@Table(name = "recommendation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recommendation_user_recommendation_id",
                columnNames = {"user_id", "recommendation_id"}),
        @UniqueConstraint(name = "uk_recommendation_user_generation_key",
                columnNames = {"user_id", "generation_key"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

    public static final int GENERATION_KEY_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "diagnosis_id", nullable = false)
    private Long diagnosisId;

    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "domain", nullable = false, length = 50)
    private String domain;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RecommendationStatus status;

    @Column(name = "monthly_cost_saving")
    private Long monthlyCostSaving;

    @Column(name = "annual_cost_saving")
    private Long annualCostSaving;

    @Column(name = "interest_saving")
    private Long interestSaving;

    @Column(name = "risk_reduction", length = 100)
    private String riskReduction;

    @Column(name = "current_forecast_date")
    private LocalDate currentForecastDate;

    @Column(name = "improved_forecast_date")
    private LocalDate improvedForecastDate;

    @Column(name = "advanced_days")
    private Integer advancedDays;

    @Column(name = "contribution_amount")
    private Long contributionAmount;

    // ERD상 NULL 허용이지만 서비스 생성 경로는 항상 채운다. NULL 행은 UNIQUE가 중복을 막지 못한다.
    @Column(name = "generation_key", length = GENERATION_KEY_LENGTH, columnDefinition = "CHAR(64)")
    private String generationKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 효과 추정치. 547에서는 모두 null이며 계산 규칙은 548이 정한다. 금액은 원 단위 정수다. */
    public record Effect(
            Long monthlyCostSaving,
            Long annualCostSaving,
            Long interestSaving,
            String riskReduction,
            LocalDate currentForecastDate,
            LocalDate improvedForecastDate,
            Integer advancedDays,
            Long contributionAmount
    ) {
        public static final Effect NONE = new Effect(null, null, null, null, null, null, null, null);

        public Effect {
            requireNonNegative("monthlyCostSaving", monthlyCostSaving);
            requireNonNegative("annualCostSaving", annualCostSaving);
            requireNonNegative("interestSaving", interestSaving);
            requireNonNegative("contributionAmount", contributionAmount);
            if (riskReduction != null && riskReduction.length() > 100) {
                throw new IllegalArgumentException("riskReduction은 100자를 넘을 수 없습니다.");
            }
        }

        private static void requireNonNegative(String name, Long value) {
            if (value != null && value < 0) {
                throw new IllegalArgumentException(name + "은(는) 음수일 수 없습니다: " + value);
            }
        }
    }

    private Recommendation(
            Long userId,
            Long diagnosisId,
            Long goalId,
            String domain,
            String type,
            String title,
            String description,
            int priority,
            Effect effect,
            String generationKey,
            LocalDateTime now
    ) {
        this.userId = userId;
        this.diagnosisId = diagnosisId;
        this.goalId = goalId;
        this.domain = domain;
        this.type = type;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = RecommendationStatus.PENDING;
        this.monthlyCostSaving = effect.monthlyCostSaving();
        this.annualCostSaving = effect.annualCostSaving();
        this.interestSaving = effect.interestSaving();
        this.riskReduction = effect.riskReduction();
        this.currentForecastDate = effect.currentForecastDate();
        this.improvedForecastDate = effect.improvedForecastDate();
        this.advancedDays = effect.advancedDays();
        this.contributionAmount = effect.contributionAmount();
        this.generationKey = generationKey;
        this.createdAt = now;
    }

    /**
     * 새 추천을 PENDING 상태로 만든다.
     *
     * @param priority 양수. 점수 계산은 548이 맡으므로 여기서는 값의 범위만 검사한다
     * @param effect null이면 {@link Effect#NONE}
     * @param generationKey 64자리 hex. {@code RecommendationGenerationKey}가 만든다
     */
    public static Recommendation pending(
            Long userId,
            Long diagnosisId,
            Long goalId,
            String domain,
            String type,
            String title,
            String description,
            int priority,
            Effect effect,
            String generationKey,
            LocalDateTime now
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (diagnosisId == null || diagnosisId <= 0) {
            throw new IllegalArgumentException("진단을 지정해야 합니다.");
        }
        requireText("domain", domain, 50);
        requireText("type", type, 50);
        requireText("title", title, 255);
        requireText("description", description, Integer.MAX_VALUE);
        if (priority <= 0) {
            throw new IllegalArgumentException("priority는 양수여야 합니다: " + priority);
        }
        if (generationKey == null || !generationKey.matches("[0-9a-f]{" + GENERATION_KEY_LENGTH + "}")) {
            throw new IllegalArgumentException("generationKey는 64자리 소문자 hex여야 합니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("생성 시각이 필요합니다.");
        }
        return new Recommendation(userId, diagnosisId, goalId, domain, type, title, description,
                priority, effect == null ? Effect.NONE : effect, generationKey, now);
    }

    private static void requireText(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + "은(는) " + maxLength + "자를 넘을 수 없습니다.");
        }
    }
}
