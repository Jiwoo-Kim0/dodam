package com.ssafy.dodam.domain.depositsaving.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "savings_payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "savings_payment_id")
    private Long id;
    @Column(name = "deposit_saving_id", nullable = false)
    private Long depositSavingId;
    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;
    @Column(name = "amount", nullable = false)
    private Long amount;
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;
    @Column(name = "status", nullable = false, length = 30)
    private String status;
}
