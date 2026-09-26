package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 금융망 계좌 개설의 결과가 불명확할 때 재개설을 막는 영속 기록. */
@Entity
@Table(name = "safebox_open_attempt")
public class SafeboxOpenAttempt {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SafeboxOpenAttempt() {
    }
}
