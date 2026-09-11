package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ERD `goal`(V5). 모으기(SAVING)·줄이기(SPENDING) 목표의 공통 행. 유형별 값은 {@link SavingGoal}·{@link SpendingGoal}
 * 하위행에 있고 연관관계 대신 같은 `goal_id`로 서비스가 함께 읽는다(같은 테이블을 여러 엔티티가 참조하는 문제를 피하기 위해).
 *
 * <p>삭제는 `deleted_at`을 채우는 soft delete다. 소비목표 비교(507)·진단(543)이 `deleted_at IS NULL`로 걸러 읽는다.
 */
@Entity
@Table(name = "goal")
public class Goal {

    public static final int NAME_MAX_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private GoalType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GoalStatus status;

    /** 모으기 목표의 현재 모인 금액. 연결 계좌 잔액 합으로 갱신된다(509). 줄이기 목표는 0으로 둔다. */
    @Column(name = "current_amount", nullable = false)
    private long currentAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Goal() {
    }

    private Goal(Long userId, String name, GoalType type, LocalDateTime now) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.status = GoalStatus.ACTIVE;
        this.currentAmount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Goal create(Long userId, String name, GoalType type, LocalDateTime now) {
        return new Goal(userId, name, type, now);
    }

    /**
     * @return 실제로 바뀌었으면 true
     */
    public boolean rename(String name, LocalDateTime now) {
        if (Objects.equals(this.name, name)) {
            return false;
        }
        this.name = name;
        this.updatedAt = now;
        return true;
    }

    /**
     * 상태 전이. 허용 여부는 호출자가 {@link GoalStatus#canTransitionTo}로 먼저 확인한다.
     *
     * @return 실제로 바뀌었으면 true(같은 상태면 false)
     */
    public boolean changeStatus(GoalStatus next, LocalDateTime now) {
        if (this.status == next) {
            return false;
        }
        this.status = next;
        this.updatedAt = now;
        return true;
    }

    public boolean updateCurrentAmount(long amount, LocalDateTime now) {
        if (this.currentAmount == amount) {
            return false;
        }
        this.currentAmount = amount;
        this.updatedAt = now;
        return true;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public GoalType getType() {
        return type;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public long getCurrentAmount() {
        return currentAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
