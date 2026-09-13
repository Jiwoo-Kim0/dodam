package com.ssafy.dodam.domain.simulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시뮬레이션 전후의 목표 예상 달성일. 한 시뮬레이션에 목표당 한 행이다.
 *
 * <p>`user_id`를 중복해 두는 것은 ERD의 복합 FK `(user_id, simulation_id)`·`(user_id, goal_id)` 때문이다.
 * 타인의 목표를 내 시뮬레이션에 붙이는 행이 DB에서 거부된다.
 *
 * <p>{@code differenceDays = after − before}. 음수면 앞당겨진 것이다. 어느 한쪽이 null(현재 속도로 달성 불가)이면
 * null이며, 그 경우 응답이 "달성 불가 ↔ 가능"의 변화를 날짜 두 개로 드러낸다.
 */
@Getter
@Entity
@Table(name = "simulation_goal_impact", uniqueConstraints = @UniqueConstraint(
        name = "uk_simulation_goal_impact_simulation_goal", columnNames = {"simulation_id", "goal_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationGoalImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "simulation_goal_impact_id")
    private Long simulationGoalImpactId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "simulation_id", nullable = false)
    private Long simulationId;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "before_forecast_date")
    private LocalDate beforeForecastDate;

    @Column(name = "after_forecast_date")
    private LocalDate afterForecastDate;

    @Column(name = "difference_days")
    private Integer differenceDays;

    private SimulationGoalImpact(Long userId, Long simulationId, Long goalId, LocalDate before, LocalDate after) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.simulationId = Objects.requireNonNull(simulationId, "simulationId");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.beforeForecastDate = before;
        this.afterForecastDate = after;
        this.differenceDays = differenceDays(before, after);
    }

    public static SimulationGoalImpact of(Long userId, Long simulationId, Long goalId, LocalDate before, LocalDate after) {
        return new SimulationGoalImpact(userId, simulationId, goalId, before, after);
    }

    public static Integer differenceDays(LocalDate before, LocalDate after) {
        if (before == null || after == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(before, after));
    }
}
