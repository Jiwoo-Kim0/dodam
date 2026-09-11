package com.ssafy.dodam.domain.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * ERD `subscription`(V30). 금융망·seed가 적재하는 정기결제 한 건. 이 서비스는 새로 만들지 않고(명세 7에 등록 API 없음)
 * 조회·수정·정지·재개·취소만 한다. 결제 이력은 {@link SubscriptionPaymentHistory}가 같은 `subscription_id`로 들고 있다.
 *
 * <p>`card_id`가 있으면 같은 사용자의 카드를 복합 외래키로 참조한다. 카드 이름·마스킹 번호는 응답 조립 시 카드 도메인에서 읽는다.
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    public static final int ID_MAX_LENGTH = 100;

    @Id
    @Column(name = "subscription_id", length = ID_MAX_LENGTH)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 30)
    private BillingCycle billingCycle;

    @Column(name = "payment_day")
    private Integer paymentDay;

    /** 다음 결제 예정일. PAUSED·CANCELED·EXPIRED면 null이다. */
    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    protected Subscription() {
    }

    // ---------- 변경 (S15P21D105-514). 모두 "실제로 바뀌었으면 true" ----------

    public boolean changePaymentAmount(long amount, LocalDateTime now) {
        if (this.paymentAmount == amount) {
            return false;
        }
        this.paymentAmount = amount;
        this.updatedAt = now;
        return true;
    }

    public boolean changePaymentDay(Integer day, LocalDateTime now) {
        if (Objects.equals(this.paymentDay, day)) {
            return false;
        }
        this.paymentDay = day;
        this.updatedAt = now;
        return true;
    }

    public boolean changeEndDate(LocalDate date, LocalDateTime now) {
        if (Objects.equals(this.endDate, date)) {
            return false;
        }
        this.endDate = date;
        this.updatedAt = now;
        return true;
    }

    public boolean changeNextPaymentDate(LocalDate date, LocalDateTime now) {
        if (Objects.equals(this.nextPaymentDate, date)) {
            return false;
        }
        this.nextPaymentDate = date;
        this.updatedAt = now;
        return true;
    }

    /** 정지. 다음 결제일은 비운다(seed 규약과 같음). */
    public void pause(LocalDateTime now) {
        this.status = SubscriptionStatus.PAUSED;
        this.nextPaymentDate = null;
        this.updatedAt = now;
    }

    /** 재개. 다음 결제일은 호출자가 계산해 넘긴다. */
    public void resume(LocalDate nextPaymentDate, LocalDateTime now) {
        this.status = SubscriptionStatus.ACTIVE;
        this.nextPaymentDate = nextPaymentDate;
        this.updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        this.status = SubscriptionStatus.CANCELED;
        this.nextPaymentDate = null;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCardId() {
        return cardId;
    }

    public String getName() {
        return name;
    }

    public long getPaymentAmount() {
        return paymentAmount;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public Integer getPaymentDay() {
        return paymentDay;
    }

    public LocalDate getNextPaymentDate() {
        return nextPaymentDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getCategory() {
        return category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    /** 월 환산 결제 금액(요약·목록 표시용). */
    public long getMonthlyEquivalentAmount() {
        return billingCycle.toMonthlyAmount(paymentAmount);
    }
}
