package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ERD `spending_goal`(V7). 줄이기 목표의 월 예산. PK는 부모 `goal.goal_id`와 같다.
 * `goal_type`은 복합 외래키 `(goal_id, goal_type) → goal(goal_id, type)`을 위해 항상 'SPENDING'이다.
 */
@Entity
@Table(name = "spending_goal")
public class SpendingGoal {

    @Id
    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "goal_type", nullable = false, length = 20)
    private String goalType;

    @Column(name = "monthly_budget", nullable = false)
    private long monthlyBudget;

    protected SpendingGoal() {
    }

    private SpendingGoal(Long goalId, long monthlyBudget) {
        this.goalId = goalId;
        this.goalType = GoalType.SPENDING.name();
        this.monthlyBudget = monthlyBudget;
    }

    public static SpendingGoal create(Long goalId, long monthlyBudget) {
        return new SpendingGoal(goalId, monthlyBudget);
    }

    /**
     * @return 실제로 바뀌었으면 true
     */
    public boolean update(Long monthlyBudget) {
        if (monthlyBudget == null || monthlyBudget == this.monthlyBudget) {
            return false;
        }
        this.monthlyBudget = monthlyBudget;
        return true;
    }

    public Long getGoalId() {
        return goalId;
    }

    public long getMonthlyBudget() {
        return monthlyBudget;
    }
}
