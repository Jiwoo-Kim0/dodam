package com.ssafy.dodam.domain.minusaccount.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마이너스통장 상세정보.
 *
 * <p>`account_id`는 AUTO_INCREMENT가 아니라 상위 `account`(`account_type = 'MINUS'`) 행의
 * 식별자를 그대로 받는다. 계좌번호·은행·상태는 상위 행이 소유하므로 여기서 매핑하지 않고
 * {@link MinusAccountOwner}로 읽는다.
 *
 * <p>`available_amount`는 파생 저장값이며 DDL의 `chk_minus_account_available_amount`가
 * `limit_amount - used_amount`와의 일치를 강제한다. 그래서 사용액을 바꾸는 모든 경로는
 * 이 클래스의 메서드를 통해 두 값을 함께 옮긴다.
 */
@Getter
@Entity
@Table(name = "minus_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinusAccount {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Column(name = "limit_amount", nullable = false)
    private Long limitAmount;

    @Column(name = "used_amount", nullable = false)
    private Long usedAmount;

    @Column(name = "available_amount", nullable = false)
    private Long availableAmount;

    // 연이율(%)이다. 금액 계산에 쓰이므로 부동소수점을 사용하지 않는다.
    @Column(name = "interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "accrued_interest", nullable = false)
    private Long accruedInterest;

    @Column(name = "next_interest_date")
    private LocalDate nextInterestDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "overdue_days", nullable = false)
    private Integer overdueDays;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 연체 여부. 별도 상태 컬럼이 없으므로 연체일수가 유일한 판정 근거다.
     */
    public boolean isOverdue() {
        return overdueDays != null && overdueDays > 0;
    }

    /**
     * 원금을 상환한다. 미수이자는 건드리지 않는다.
     *
     * <p>호출 전에 서비스가 금액을 검증한다. 여기서는 CHECK 제약을 만족시키기 위해
     * 사용액과 가용액을 함께 옮기는 책임만 진다.
     */
    public void repayPrincipal(long amount, LocalDateTime now) {
        if (amount <= 0 || amount > usedAmount) {
            throw new IllegalArgumentException("상환 금액이 사용액 범위를 벗어났습니다.");
        }
        this.usedAmount -= amount;
        this.availableAmount = this.limitAmount - this.usedAmount;
        this.updatedAt = now;
    }

    /**
     * 미수이자를 납부한다. 사용액(원금)은 바뀌지 않는다.
     */
    public void payInterest(long amount, LocalDateTime now) {
        if (amount <= 0 || amount > accruedInterest) {
            throw new IllegalArgumentException("납부 금액이 미수이자 범위를 벗어났습니다.");
        }
        this.accruedInterest -= amount;
        this.updatedAt = now;
    }
}
