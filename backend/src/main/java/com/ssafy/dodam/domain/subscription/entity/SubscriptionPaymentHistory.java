package com.ssafy.dodam.domain.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * ERD `subscription_payment_history`(V30). 정기결제 한 건의 결제 시도 기록. `result_code`가 null이거나 SUCCESS면 성공으로 본다
 * (seed는 'SUCCESS'를 넣고, 금융망 적재 시 코드가 없을 수 있다).
 */
@Entity
@Table(name = "subscription_payment_history")
public class SubscriptionPaymentHistory {

    public static final String SUCCESS_CODE = "SUCCESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_history_id")
    private Long id;

    @Column(name = "subscription_id", nullable = false, length = Subscription.ID_MAX_LENGTH)
    private String subscriptionId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    @Column(name = "result_code", length = 50)
    private String resultCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "result_message", length = 500)
    private String resultMessage;

    protected SubscriptionPaymentHistory() {
    }

    public boolean isSuccess() {
        return resultCode == null || SUCCESS_CODE.equalsIgnoreCase(resultCode);
    }

    public Long getId() {
        return id;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public long getPaymentAmount() {
        return paymentAmount;
    }

    public String getResultCode() {
        return resultCode;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getResultMessage() {
        return resultMessage;
    }
}
