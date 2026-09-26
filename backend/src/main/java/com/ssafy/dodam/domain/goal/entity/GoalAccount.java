package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * ERD `goal_account`(V8). 모으기 목표에 연결된 계좌 한 건. 계좌 잔액이 목표의 모인 금액(`goal.current_amount`)에 합산된다.
 *
 * <p>`account`는 ID·유형 컬럼만 들고 연관관계를 걸지 않는다(계좌 도메인 소유 테이블). 복합 외래키는 V8 DDL이 보장한다.
 *
 * <p>`contribution_amount`(계좌 잔액 중 목표에 기여하는 금액)는 명세 12 Body(`accountIds[]`)에 대응 필드가 없어 연결만으로는
 * null이고, 그때는 잔액 전체를 기여로 본다. 기존 숫자 할당 행은 읽기 호환을 위해 보존한다.
 */
@Entity
@IdClass(GoalAccountId.class)
@Table(name = "goal_account")
public class GoalAccount {

    @Id
    @Column(name = "goal_id")
    private Long goalId;

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Column(name = "contribution_amount")
    private Long contributionAmount;

    protected GoalAccount() {
    }

    private GoalAccount(Long userId, Long goalId, Long accountId, String accountType) {
        this.userId = userId;
        this.goalId = goalId;
        this.accountId = accountId;
        this.accountType = accountType;
    }

    public static GoalAccount link(Long userId, Long goalId, Long accountId, String accountType) {
        return new GoalAccount(userId, goalId, accountId, accountType);
    }

    public static GoalAccount allocate(Long userId, Long goalId, Long accountId, long amount) {
        GoalAccount link = new GoalAccount(userId, goalId, accountId, "DEMAND_DEPOSIT");
        link.contributionAmount = amount;
        return link;
    }

    public void setContributionAmount(long amount) {
        this.contributionAmount = amount;
    }

    public Long getGoalId() {
        return goalId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAccountType() {
        return accountType;
    }

    public Long getContributionAmount() {
        return contributionAmount;
    }

}
