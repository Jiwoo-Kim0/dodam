package com.ssafy.dodam.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * ERD `financial_profile`. 사용자가 직접 입력하는 금융 기본정보(소득·급여일·고정지출·비상자금 기준).
 * 모든 값은 미입력(NULL)일 수 있으며 회원가입 시 빈 행을 만들어 둔다.
 */
@Entity
@Table(name = "financial_profile")
public class FinancialProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "monthly_income")
    private Long monthlyIncome;

    @Column(name = "annual_income")
    private Long annualIncome;

    @Column(name = "payday")
    private Integer payday;

    @Column(name = "fixed_expense")
    private Long fixedExpense;

    @Column(name = "emergency_fund_target")
    private Long emergencyFundTarget;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FinancialProfile() {
    }

    private FinancialProfile(Long userId) {
        this.userId = userId;
    }

    public static FinancialProfile empty(Long userId) {
        return new FinancialProfile(userId);
    }

    /**
     * null인 항목은 건드리지 않는다 (PATCH 시맨틱).
     */
    public void update(
            Long monthlyIncome,
            Long annualIncome,
            Integer payday,
            Long fixedExpense,
            Long emergencyFundTarget
    ) {
        if (monthlyIncome != null) {
            this.monthlyIncome = monthlyIncome;
        }
        if (annualIncome != null) {
            this.annualIncome = annualIncome;
        }
        if (payday != null) {
            this.payday = payday;
        }
        if (fixedExpense != null) {
            this.fixedExpense = fixedExpense;
        }
        if (emergencyFundTarget != null) {
            this.emergencyFundTarget = emergencyFundTarget;
        }
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMonthlyIncome() {
        return monthlyIncome;
    }

    public Long getAnnualIncome() {
        return annualIncome;
    }

    public Integer getPayday() {
        return payday;
    }

    public Long getFixedExpense() {
        return fixedExpense;
    }

    public Long getEmergencyFundTarget() {
        return emergencyFundTarget;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
