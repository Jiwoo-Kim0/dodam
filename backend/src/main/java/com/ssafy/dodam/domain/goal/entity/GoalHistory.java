package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD `goal_history`(V9). 목표의 상태가 바뀐 시점의 스냅샷. 한 날짜에 여러 건이 있을 수 있다.
 *
 * @see GoalHistoryEvent 어떤 시점에 기록하는지
 */
@Entity
@Table(name = "goal_history")
public class GoalHistory {

    public static final int EVENT_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_history_id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Column(name = "current_amount", nullable = false)
    private long currentAmount;

    /** 모으기 목표의 진행률(%, 0~100, 소수 4자리). 줄이기 목표는 null. */
    @Column(name = "progress_rate", precision = 10, scale = 4)
    private BigDecimal progressRate;

    /** 기록 시점의 예상 달성일. 저축속도가 0이거나 줄이기 목표면 null. */
    @Column(name = "forecast_date")
    private LocalDate forecastDate;

    @Column(name = "event", length = EVENT_MAX_LENGTH)
    private String event;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected GoalHistory() {
    }

    private GoalHistory(Long goalId, LocalDate recordedDate, long currentAmount, BigDecimal progressRate,
                        LocalDate forecastDate, String event, LocalDateTime createdAt) {
        this.goalId = goalId;
        this.recordedDate = recordedDate;
        this.currentAmount = currentAmount;
        this.progressRate = progressRate;
        this.forecastDate = forecastDate;
        this.event = event;
        this.createdAt = createdAt;
    }

    public static GoalHistory record(Long goalId, long currentAmount, BigDecimal progressRate, LocalDate forecastDate,
                                     GoalHistoryEvent event, LocalDateTime now) {
        return new GoalHistory(goalId, now.toLocalDate(), currentAmount, progressRate, forecastDate, event.name(), now);
    }

    public Long getId() {
        return id;
    }

    public Long getGoalId() {
        return goalId;
    }

    public LocalDate getRecordedDate() {
        return recordedDate;
    }

    public long getCurrentAmount() {
        return currentAmount;
    }

    public BigDecimal getProgressRate() {
        return progressRate;
    }

    public LocalDate getForecastDate() {
        return forecastDate;
    }

    public String getEvent() {
        return event;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
