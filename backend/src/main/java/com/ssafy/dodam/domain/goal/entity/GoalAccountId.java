package com.ssafy.dodam.domain.goal.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * `goal_account`의 복합 PK (goal_id, account_id).
 */
public class GoalAccountId implements Serializable {

    private Long goalId;
    private Long accountId;

    protected GoalAccountId() {
    }

    public GoalAccountId(Long goalId, Long accountId) {
        this.goalId = goalId;
        this.accountId = accountId;
    }

    public Long getGoalId() {
        return goalId;
    }

    public Long getAccountId() {
        return accountId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoalAccountId that)) {
            return false;
        }
        return Objects.equals(goalId, that.goalId) && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalId, accountId);
    }
}
