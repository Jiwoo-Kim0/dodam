package com.ssafy.dodam.domain.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;

/**
 * ERD `transaction`. 계좌·카드 거래와 수동 등록 거래를 한 원장에 담는다.
 *
 * <p>`account`·`card`·`transaction_category`는 ID 컬럼만 들고 연관관계를 걸지 않는다. 계좌·카드는 다른
 * 티켓(501, 527)이 소유한 테이블이고, 같은 테이블을 여러 엔티티가 매핑하면 H2 테스트 스키마가 매핑 순서에
 * 따라 갈리는 문제가 이미 보고돼 있다(remaining-work.md). 복합 외래키는 V6 DDL이 보장한다.
 *
 * <p>거래 ID는 금융망 `transactionUniqueNo` 또는 seed·수동 등록 규칙으로 정해지는 문자열이라 자동 생성하지 않는다.
 */
@Entity
@Table(name = "`transaction`")
public class Transaction {

    public static final int MEMO_MAX_LENGTH = 500;

    @Id
    @Column(name = "transaction_id", length = 100)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "transaction_time")
    private LocalTime transactionTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "spending_type", length = 20)
    private SpendingType spendingType;

    @Column(name = "summary", length = 255)
    private String summary;

    @Column(name = "memo", length = MEMO_MAX_LENGTH)
    private String memo;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @ColumnDefault("false")
    @Column(name = "internal_transfer", nullable = false)
    private boolean internalTransfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private TransactionSourceType sourceType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Transaction() {
    }

    private Transaction(
            String id,
            Long userId,
            LocalDate transactionDate,
            LocalTime transactionTime,
            TransactionType transactionType,
            long amount,
            String merchantName,
            Long categoryId,
            SpendingType spendingType,
            String paymentMethod,
            String memo,
            LocalDateTime now
    ) {
        this.id = id;
        this.userId = userId;
        this.transactionDate = transactionDate;
        this.transactionTime = transactionTime;
        this.transactionType = transactionType;
        this.amount = amount;
        this.merchantName = merchantName;
        this.categoryId = categoryId;
        this.spendingType = spendingType;
        this.paymentMethod = paymentMethod;
        this.memo = memo;
        this.hidden = false;
        this.internalTransfer = false;
        this.sourceType = TransactionSourceType.MANUAL;
        this.createdAt = now;
    }

    /**
     * 사용자가 직접 등록한 거래(S15P21D105-505). 계좌·카드에 연결되지 않고 잔액 정보도 없다.
     * 값 검증(양수 금액, 미래 아닌 거래일, 카테고리 존재, 메모 규칙)은 서비스가 끝낸 상태여야 한다.
     *
     * @param transactionTime 선택. 사용자가 고른 거래 시각. 없으면 null이며 목록 정렬에서 같은 날 거래의 맨 뒤로 간다
     * @param category        선택. 있으면 기본 소비유형을 함께 적용한다
     */
    public static Transaction createManual(
            String id,
            Long userId,
            LocalDate transactionDate,
            LocalTime transactionTime,
            TransactionType transactionType,
            long amount,
            String merchantName,
            TransactionCategory category,
            String paymentMethod,
            String memo,
            LocalDateTime now
    ) {
        return new Transaction(
                id,
                userId,
                transactionDate,
                transactionTime,
                transactionType,
                amount,
                merchantName,
                category == null ? null : category.getId(),
                category == null ? null : category.getDefaultSpendingType(),
                paymentMethod,
                memo,
                now
        );
    }

    public boolean isManual() {
        return this.sourceType == TransactionSourceType.MANUAL;
    }

    /**
     * 수동 거래의 기본 항목을 바꾼다. null은 "변경 없음"이다.
     *
     * @return 실제로 값이 바뀌었으면 true.
     */
    public boolean updateManualDetails(
            LocalDate transactionDate,
            LocalTime transactionTime,
            TransactionType transactionType,
            Long amount,
            LocalDateTime now
    ) {
        boolean changed = false;
        if (transactionDate != null && !transactionDate.equals(this.transactionDate)) {
            this.transactionDate = transactionDate;
            changed = true;
        }
        if (transactionTime != null && !transactionTime.equals(this.transactionTime)) {
            this.transactionTime = transactionTime;
            changed = true;
        }
        if (transactionType != null && transactionType != this.transactionType) {
            this.transactionType = transactionType;
            changed = true;
        }
        if (amount != null && amount != this.amount) {
            this.amount = amount;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    /**
     * 가맹점명을 바꾼다. 정규화(trim·빈 값 → null)는 호출자가 끝낸 상태여야 한다.
     */
    public boolean changeMerchantName(String normalizedMerchantName, LocalDateTime now) {
        if (Objects.equals(this.merchantName, normalizedMerchantName)) {
            return false;
        }
        this.merchantName = normalizedMerchantName;
        this.updatedAt = now;
        return true;
    }

    /**
     * 결제수단을 바꾼다. 정규화(trim·대문자·빈 값 → null)는 호출자가 끝낸 상태여야 한다.
     */
    public boolean changePaymentMethod(String normalizedPaymentMethod, LocalDateTime now) {
        if (Objects.equals(this.paymentMethod, normalizedPaymentMethod)) {
            return false;
        }
        this.paymentMethod = normalizedPaymentMethod;
        this.updatedAt = now;
        return true;
    }

    /**
     * 소비 카테고리를 바꾼다. 카테고리에 기본 소비유형이 있으면 `spending_type`도 그 값으로 맞춘다.
     * 기본 소비유형이 없는 카테고리면 기존 소비유형을 유지한다.
     *
     * @return 실제로 값이 바뀌었으면 true. 같은 카테고리를 다시 지정한 중복 요청이면 false이고 아무것도 갱신하지 않는다.
     */
    public boolean changeCategory(TransactionCategory category, LocalDateTime now) {
        if (Objects.equals(this.categoryId, category.getId())) {
            return false;
        }
        this.categoryId = category.getId();
        if (category.getDefaultSpendingType() != null) {
            this.spendingType = category.getDefaultSpendingType();
        }
        this.updatedAt = now;
        return true;
    }

    /**
     * 메모를 바꾼다. 정규화(trim·빈 값 → null)는 호출자가 끝낸 상태여야 한다.
     *
     * @return 실제로 값이 바뀌었으면 true.
     */
    public boolean changeMemo(String normalizedMemo, LocalDateTime now) {
        if (Objects.equals(this.memo, normalizedMemo)) {
            return false;
        }
        this.memo = normalizedMemo;
        this.updatedAt = now;
        return true;
    }

    /**
     * 숨김 상태를 바꾼다.
     *
     * @return 실제로 상태가 전이됐으면 true. 이미 같은 상태면 false이고 아무것도 갱신하지 않는다.
     */
    public boolean changeHidden(boolean hidden, LocalDateTime now) {
        if (this.hidden == hidden) {
            return false;
        }
        this.hidden = hidden;
        this.updatedAt = now;
        return true;
    }

    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getCardId() {
        return cardId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public LocalTime getTransactionTime() {
        return transactionTime;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public long getAmount() {
        return amount;
    }

    public Long getBalanceAfter() {
        return balanceAfter;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public SpendingType getSpendingType() {
        return spendingType;
    }

    public String getSummary() {
        return summary;
    }

    public String getMemo() {
        return memo;
    }

    public boolean isHidden() {
        return hidden;
    }

    public TransactionSourceType getSourceType() {
        return sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
