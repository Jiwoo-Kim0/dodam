package com.ssafy.dodam.domain.improvement.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자가 실제로 수행한 개선 행동 1건(API 19).
 *
 * <p><b>추천과 중복 저장하지 않는다.</b> `goalId`·`domain`·`type`·예상 효과는 `recommendation`에 있고 여기서는
 * `recommendationId`로만 잇는다. 이 엔티티의 금액 컬럼은 전부 <b>실측값</b>이다 — 응답의 {@code expected*}는 추천에서,
 * {@code actual*}은 여기서 읽는다. 둘을 더하거나 한쪽이 없을 때 다른 쪽으로 메우지 않는다.
 *
 * <p>{@code title}만 추천에서 복사한다. 수락 당시 사용자가 본 문구를 보존하기 위해서다.
 *
 * <p>{@code @UniqueConstraint}를 엔티티에도 선언한다. H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기 때문에
 * 여기 없으면 "추천 1건당 행동 1건"이 테스트에서 검증되지 않는다(Recommendation이 같은 이유로 선언한다).
 *
 * <p>스냅샷 두 개는 실행 전후의 재무 관측치다. 형식은 {@code ImprovementSnapshot}이 정하며 스키마를 엔티티로
 * 고정하지 않는다(Diagnosis.inputSnapshot과 같은 이유로 {@code JsonNode}를 쓴다 — `@JdbcTypeCode(JSON)` + String은
 * JSON 문자열 리터럴로 한 번 더 감싸져 MySQL에서 객체가 되지 않는다).
 */
@Getter
@Entity
@Table(name = "improvement_action", uniqueConstraints = {
        @UniqueConstraint(name = "uk_improvement_action_recommendation_id", columnNames = {"recommendation_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImprovementAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long actionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImprovementActionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "JSON")
    private JsonNode beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", columnDefinition = "JSON")
    private JsonNode afterSnapshot;

    @Column(name = "spending_reduction")
    private Long spendingReduction;

    @Column(name = "saving_increase")
    private Long savingIncrease;

    @Column(name = "debt_reduction")
    private Long debtReduction;

    @Column(name = "monthly_cost_saving")
    private Long monthlyCostSaving;

    @Column(name = "annual_cost_saving")
    private Long annualCostSaving;

    @Column(name = "interest_saving")
    private Long interestSaving;

    @Column(name = "goal_advanced_days")
    private Integer goalAdvancedDays;

    @Column(name = "planned_at", nullable = false)
    private LocalDateTime plannedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 측정 1회의 결과. 모두 원 단위 정수이며 음수가 될 수 없다(악화된 경우는 0이고 방향은 비교 API가 보여 준다). */
    public record Measurement(
            long spendingReduction,
            long savingIncrease,
            long debtReduction,
            long interestSaving,
            long monthlyCostSaving,
            long annualCostSaving,
            Integer goalAdvancedDays
    ) {
        public Measurement {
            requireNonNegative("spendingReduction", spendingReduction);
            requireNonNegative("savingIncrease", savingIncrease);
            requireNonNegative("debtReduction", debtReduction);
            requireNonNegative("interestSaving", interestSaving);
            requireNonNegative("monthlyCostSaving", monthlyCostSaving);
            requireNonNegative("annualCostSaving", annualCostSaving);
        }

        private static void requireNonNegative(String name, long value) {
            if (value < 0) {
                throw new IllegalArgumentException(name + "은(는) 음수일 수 없습니다: " + value);
            }
        }
    }

    private ImprovementAction(Long userId, Long recommendationId, String title, JsonNode beforeSnapshot,
            LocalDateTime now) {
        this.userId = userId;
        this.recommendationId = recommendationId;
        this.title = title;
        this.status = ImprovementActionStatus.PLANNED;
        this.beforeSnapshot = beforeSnapshot;
        this.plannedAt = now;
        this.createdAt = now;
    }

    /**
     * 추천을 수락한 시점의 행동을 만든다.
     *
     * @param beforeSnapshot 수락 시점의 재무 관측치. 측정 기준선이 된다. 만들 수 없으면 null이며 그때는 전후 비교가 불가하다
     */
    public static ImprovementAction plan(Long userId, Long recommendationId, String title, JsonNode beforeSnapshot,
            LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (recommendationId == null || recommendationId <= 0) {
            throw new IllegalArgumentException("개선안을 지정해야 합니다.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어 있을 수 없습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("수락 시각이 필요합니다.");
        }
        return new ImprovementAction(userId, recommendationId,
                title.length() > 255 ? title.substring(0, 255) : title, beforeSnapshot, now);
    }

    /** 관측이 시작됐다(측정 기준 기간의 첫날이 지났다). 이미 진행·종료 상태면 아무것도 하지 않는다. */
    public void start(LocalDateTime startedAt, LocalDateTime now) {
        if (status != ImprovementActionStatus.PLANNED) {
            return;
        }
        this.status = ImprovementActionStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.updatedAt = now;
    }

    /** 추천이 COMPLETED가 됐다. 취소된 행동은 되살리지 않는다. */
    public void complete(LocalDateTime now) {
        if (status == ImprovementActionStatus.CANCELED || status == ImprovementActionStatus.COMPLETED) {
            return;
        }
        if (startedAt == null) {
            // chk_improvement_action_dates(started_at <= completed_at)를 만족시키면서, 실행 시작을 모를 때
            // 수락 시각을 시작으로 본다. 수락과 완료 사이에 실행됐다는 것은 상태 전이가 보장한다.
            this.startedAt = plannedAt;
        }
        this.status = ImprovementActionStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    /** 수락을 되돌렸다(추천 REJECTED·DEFERRED). 이미 완료한 행동은 취소하지 않는다. */
    public void cancel(LocalDateTime now) {
        if (status.isTerminal()) {
            return;
        }
        this.status = ImprovementActionStatus.CANCELED;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    /** 다시 수락됐다(DEFERRED → ACCEPTED). 취소 흔적을 지우고 계획 상태로 되돌린다. */
    public void replan(JsonNode beforeSnapshot, LocalDateTime now) {
        if (status == ImprovementActionStatus.COMPLETED) {
            return;
        }
        this.status = ImprovementActionStatus.PLANNED;
        this.canceledAt = null;
        this.startedAt = null;
        this.plannedAt = now;
        this.beforeSnapshot = beforeSnapshot;
        this.updatedAt = now;
    }

    /** 실행 후 관측치로 실측 효과를 기록한다. 예상치를 덮어쓰지 않는다(예상치는 추천 행에 있다). */
    public void measure(Measurement measurement, JsonNode afterSnapshot, LocalDateTime now) {
        if (measurement == null || now == null) {
            throw new IllegalArgumentException("측정 결과와 측정 시각이 필요합니다.");
        }
        this.spendingReduction = measurement.spendingReduction();
        this.savingIncrease = measurement.savingIncrease();
        this.debtReduction = measurement.debtReduction();
        this.interestSaving = measurement.interestSaving();
        this.monthlyCostSaving = measurement.monthlyCostSaving();
        this.annualCostSaving = measurement.annualCostSaving();
        this.goalAdvancedDays = measurement.goalAdvancedDays();
        this.afterSnapshot = afterSnapshot;
        this.measuredAt = now;
        this.updatedAt = now;
    }

    public boolean isMeasured() {
        return measuredAt != null;
    }
}
