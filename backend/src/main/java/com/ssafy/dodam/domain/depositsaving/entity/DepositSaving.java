package com.ssafy.dodam.domain.depositsaving.entity;

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

@Getter
@Entity
@Table(name = "deposit_saving")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositSaving {
    @Id
    @Column(name = "account_id")
    private Long accountId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "withdrawal_account_id")
    private Long withdrawalAccountId;
    @Column(name = "withdrawal_account_type", length = 30)
    private String withdrawalAccountType;
    @Column(name = "financial_product_id")
    private Long financialProductId;
    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;
    @Column(name = "interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal interestRate;
    @Column(name = "monthly_amount")
    private Long monthlyAmount;
    @Column(name = "payment_day")
    private Integer paymentDay;
    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;
    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
