package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 금융망 이체 없이 도담 전용 계좌 안에서 목표 배정액을 바꾼 기록. */
@Entity
@Table(name = "safebox_movement")
public class SafeboxMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "amount_before", nullable = false)
    private long amountBefore;

    @Column(name = "amount_after", nullable = false)
    private long amountAfter;

    @Column(name = "delta", nullable = false)
    private long delta;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected SafeboxMovement() {
    }

    public SafeboxMovement(Long userId, Long goalId, Long accountId, long before, long after, LocalDateTime now) {
        this.userId = userId;
        this.goalId = goalId;
        this.accountId = accountId;
        this.amountBefore = before;
        this.amountAfter = after;
        this.delta = after - before;
        this.occurredAt = now;
    }
}
