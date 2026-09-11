package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * ERD `saving_goal`(V7). 모으기 목표의 목표 금액·목표일. PK는 부모 `goal.goal_id`와 같다.
 * `goal_type`은 복합 외래키 `(goal_id, goal_type) → goal(goal_id, type)`을 위해 항상 'SAVING'이다.
 */
@Entity
@Table(name = "saving_goal")
public class SavingGoal {

    @Id
    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "goal_type", nullable = false, length = 20)
    private String goalType;

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    protected SavingGoal() {
    }

    private SavingGoal(Long goalId, long targetAmount, LocalDate targetDate) {
        this.goalId = goalId;
        this.goalType = GoalType.SAVING.name();
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    public static SavingGoal create(Long goalId, long targetAmount, LocalDate targetDate) {
        return new SavingGoal(goalId, targetAmount, targetDate);
    }

    /**
     * null은 "변경 없음".
     *
     * @return 실제로 바뀐 값이 있으면 true
     */
    public boolean update(Long targetAmount, LocalDate targetDate) {
        boolean changed = false;
        if (targetAmount != null && targetAmount != this.targetAmount) {
            this.targetAmount = targetAmount;
            changed = true;
        }
        if (targetDate != null && !Objects.equals(targetDate, this.targetDate)) {
            this.targetDate = targetDate;
            changed = true;
        }
        return changed;
    }

    public Long getGoalId() {
        return goalId;
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }
}
