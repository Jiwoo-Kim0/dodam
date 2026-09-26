package com.ssafy.dodam.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 한 사용자에게 하나만 있는 도담 전용 금융망 계좌의 지정 정보. */
@Entity
@Table(name = "safebox_account")
public class SafeboxAccount {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType = "DEMAND_DEPOSIT";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SafeboxAccount() {
    }

    public SafeboxAccount(Long userId, Long accountId, LocalDateTime createdAt) {
        this.userId = userId;
        this.accountId = accountId;
        this.createdAt = createdAt;
    }

    public Long getAccountId() {
        return accountId;
    }
}
